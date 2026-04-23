package com.southwestasiafloat.backend.application.service;

import com.southwestasiafloat.backend.dto.response.DraftAnalyzeJobStatusResponse;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeJobSubmitResponse;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import com.southwestasiafloat.backend.infrastructure.mq.OcrJobPublisher;
import com.southwestasiafloat.backend.infrastructure.mq.OcrJobResultMessage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@Slf4j
@Service
public class AsyncDraftApplicationService {

    private final DraftApplicationService draftApplicationService;
    private final AnalyzeJobStore analyzeJobStore;
    private final OcrJobPublisher ocrJobPublisher;
    private final AnalyzeJobUpdateNotifier analyzeJobUpdateNotifier;
    private final AnalysisRequestGuard analysisRequestGuard;
    private final MeterRegistry meterRegistry;

    public AsyncDraftApplicationService(DraftApplicationService draftApplicationService,
                                        AnalyzeJobStore analyzeJobStore,
                                        OcrJobPublisher ocrJobPublisher,
                                        AnalyzeJobUpdateNotifier analyzeJobUpdateNotifier,
                                        AnalysisRequestGuard analysisRequestGuard,
                                        MeterRegistry meterRegistry) {
        this.draftApplicationService = draftApplicationService;
        this.analyzeJobStore = analyzeJobStore;
        this.ocrJobPublisher = ocrJobPublisher;
        this.analyzeJobUpdateNotifier = analyzeJobUpdateNotifier;
        this.analysisRequestGuard = analysisRequestGuard;
        this.meterRegistry = meterRegistry;
    }

    public DraftAnalyzeJobSubmitResponse submitAnalyzeJob(MultipartFile file, String sessionId) throws Exception {
        analysisRequestGuard.validateUpload(file);
        analysisRequestGuard.ensureAsyncCapacity(sessionId);
        byte[] imageBytes = file.getBytes();
        String cacheKey = draftApplicationService.buildAnalyzeCacheKey(sessionId, imageBytes);

        Optional<DraftAnalyzeResponse> cached = draftApplicationService.findCachedAnalyzeResult(cacheKey);
        if (cached.isPresent()) {
            AsyncDraftAnalyzeJob cachedJob = analyzeJobStore.createCompleted(sessionId, cacheKey, cached.get());
            analyzeJobUpdateNotifier.notifyJob(cachedJob);
            return new DraftAnalyzeJobSubmitResponse(
                    cachedJob.getJobId(),
                    cachedJob.getStatus().name(),
                    "Analyze result reused from cache."
            );
        }

        AsyncDraftAnalyzeJob job = analyzeJobStore.createQueued(sessionId, cacheKey);
        analyzeJobUpdateNotifier.notifyJob(job);

        try {
            ocrJobPublisher.publish(
                    job.getJobId(),
                    sessionId,
                    resolveFilename(file),
                    imageBytes
            );
            meterRegistry.counter("arena.analyze.jobs.submitted").increment();
            return new DraftAnalyzeJobSubmitResponse(
                    job.getJobId(),
                    job.getStatus().name(),
                    "OCR job queued."
            );
        } catch (Exception ex) {
            job.markFailed("Failed to publish OCR job: " + ex.getMessage());
            analyzeJobStore.save(job);
            meterRegistry.counter("arena.analyze.jobs.failed", "stage", "publish").increment();
            analyzeJobUpdateNotifier.notifyJob(job);
            throw ex;
        }
    }

    public DraftAnalyzeJobStatusResponse getAnalyzeJob(String jobId) {
        return DraftAnalyzeJobStatusResponse.from(analyzeJobStore.require(jobId));
    }

    public void handleOcrResult(OcrJobResultMessage message) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Optional<AsyncDraftAnalyzeJob> jobOptional = analyzeJobStore.findById(message.getJobId());
            if (jobOptional.isEmpty()) {
                log.warn("Ignored OCR result for unknown jobId={}", message.getJobId());
                meterRegistry.counter("arena.ocr.results.ignored", "reason", "unknown-job").increment();
                return;
            }

            AsyncDraftAnalyzeJob job = jobOptional.get();
            if (isTerminal(job)) {
                log.info("Ignored duplicate OCR result for terminal jobId={} status={}", job.getJobId(), job.getStatus());
                meterRegistry.counter("arena.ocr.results.ignored", "reason", "terminal-job").increment();
                return;
            }

            if (!message.isSuccess()) {
                job.markFailed(resolveError(message.getErrorMessage()));
                analyzeJobStore.save(job);
                meterRegistry.counter("arena.analyze.jobs.failed", "stage", "ocr").increment();
                analyzeJobUpdateNotifier.notifyJob(job);
                return;
            }

            if (message.getResultJson() == null || message.getResultJson().isBlank()) {
                job.markFailed("OCR worker returned an empty result.");
                analyzeJobStore.save(job);
                meterRegistry.counter("arena.analyze.jobs.failed", "stage", "ocr-empty").increment();
                analyzeJobUpdateNotifier.notifyJob(job);
                return;
            }

            job.markAnalyzing();
            analyzeJobStore.save(job);
            analyzeJobUpdateNotifier.notifyJob(job);

            try {
                DraftAnalyzeResponse response = draftApplicationService.analyzeOcrResult(
                        message.getResultJson(),
                        job.getSessionId(),
                        job.getCacheKey()
                );
                job.markCompleted(response);
                meterRegistry.counter("arena.analyze.jobs.completed").increment();
                log.info("Async analyze job {} completed", job.getJobId());
            } catch (Exception ex) {
                log.warn("Async analyze job {} failed after OCR completed", job.getJobId(), ex);
                job.markFailed("Analyze failed after OCR completed: " + ex.getMessage());
                meterRegistry.counter("arena.analyze.jobs.failed", "stage", "analysis").increment();
            }

            analyzeJobStore.save(job);
            analyzeJobUpdateNotifier.notifyJob(job);
        } finally {
            sample.stop(meterRegistry.timer("arena.analyze.jobs.handle_ocr_result"));
        }
    }

    private boolean isTerminal(AsyncDraftAnalyzeJob job) {
        return job.getStatus() == AnalyzeJobStatus.COMPLETED
                || job.getStatus() == AnalyzeJobStatus.FAILED;
    }

    private String resolveFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            return "screenshot.png";
        }
        return filename;
    }

    private String resolveError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "OCR job failed.";
        }
        return errorMessage;
    }
}

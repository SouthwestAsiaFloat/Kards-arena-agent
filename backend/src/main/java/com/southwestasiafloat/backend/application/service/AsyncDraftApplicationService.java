package com.southwestasiafloat.backend.application.service;

import com.southwestasiafloat.backend.dto.response.DraftAnalyzeJobStatusResponse;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeJobSubmitResponse;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import com.southwestasiafloat.backend.infrastructure.mq.OcrJobPublisher;
import com.southwestasiafloat.backend.infrastructure.mq.OcrJobResultMessage;
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

    public AsyncDraftApplicationService(DraftApplicationService draftApplicationService,
                                        AnalyzeJobStore analyzeJobStore,
                                        OcrJobPublisher ocrJobPublisher,
                                        AnalyzeJobUpdateNotifier analyzeJobUpdateNotifier) {
        this.draftApplicationService = draftApplicationService;
        this.analyzeJobStore = analyzeJobStore;
        this.ocrJobPublisher = ocrJobPublisher;
        this.analyzeJobUpdateNotifier = analyzeJobUpdateNotifier;
    }

    public DraftAnalyzeJobSubmitResponse submitAnalyzeJob(MultipartFile file, String sessionId) throws Exception {
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
            return new DraftAnalyzeJobSubmitResponse(
                    job.getJobId(),
                    job.getStatus().name(),
                    "OCR job queued."
            );
        } catch (Exception ex) {
            job.markFailed("Failed to publish OCR job: " + ex.getMessage());
            analyzeJobUpdateNotifier.notifyJob(job);
            throw ex;
        }
    }

    public DraftAnalyzeJobStatusResponse getAnalyzeJob(String jobId) {
        return DraftAnalyzeJobStatusResponse.from(analyzeJobStore.require(jobId));
    }

    public void handleOcrResult(OcrJobResultMessage message) {
        Optional<AsyncDraftAnalyzeJob> jobOptional = analyzeJobStore.findById(message.getJobId());
        if (jobOptional.isEmpty()) {
            log.warn("Ignored OCR result for unknown jobId={}", message.getJobId());
            return;
        }

        AsyncDraftAnalyzeJob job = jobOptional.get();

        if (!message.isSuccess()) {
            job.markFailed(resolveError(message.getErrorMessage()));
            analyzeJobUpdateNotifier.notifyJob(job);
            return;
        }

        if (message.getResultJson() == null || message.getResultJson().isBlank()) {
            job.markFailed("OCR worker returned an empty result.");
            analyzeJobUpdateNotifier.notifyJob(job);
            return;
        }

        job.markAnalyzing();
        analyzeJobUpdateNotifier.notifyJob(job);

        try {
            DraftAnalyzeResponse response = draftApplicationService.analyzeOcrResult(
                    message.getResultJson(),
                    job.getSessionId(),
                    job.getCacheKey()
            );
            job.markCompleted(response);
            log.info("Async analyze job {} completed", job.getJobId());
        } catch (Exception ex) {
            log.warn("Async analyze job {} failed after OCR completed", job.getJobId(), ex);
            job.markFailed("Analyze failed after OCR completed: " + ex.getMessage());
        }

        analyzeJobUpdateNotifier.notifyJob(job);
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

package com.southwestasiafloat.backend.application.analysis;

/**
 * 异步草稿分析应用服务，负责任务提交、回调处理和状态流转。
 */

import com.southwestasiafloat.backend.application.analysis.model.AnalyzeJobStatus;
import com.southwestasiafloat.backend.application.analysis.model.AsyncDraftAnalyzeJob;
import com.southwestasiafloat.backend.application.analysis.support.AnalyzeJobStore;
import com.southwestasiafloat.backend.application.analysis.support.AnalyzeJobUpdateNotifier;
import com.southwestasiafloat.backend.application.analysis.support.AnalysisRequestGuard;
import com.southwestasiafloat.backend.application.ratelimit.AnalyzeRateLimitService;
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
    private final AnalyzeRateLimitService analyzeRateLimitService;
    private final MeterRegistry meterRegistry;

    public AsyncDraftApplicationService(DraftApplicationService draftApplicationService,
                                        AnalyzeJobStore analyzeJobStore,
                                        OcrJobPublisher ocrJobPublisher,
                                        AnalyzeJobUpdateNotifier analyzeJobUpdateNotifier,
                                        AnalysisRequestGuard analysisRequestGuard,
                                        AnalyzeRateLimitService analyzeRateLimitService,
                                        MeterRegistry meterRegistry) {
        this.draftApplicationService = draftApplicationService;
        this.analyzeJobStore = analyzeJobStore;
        this.ocrJobPublisher = ocrJobPublisher;
        this.analyzeJobUpdateNotifier = analyzeJobUpdateNotifier;
        this.analysisRequestGuard = analysisRequestGuard;
        this.analyzeRateLimitService = analyzeRateLimitService;
        this.meterRegistry = meterRegistry;
    }

    // 提交分析任务，返回任务 ID 和初始状态。分析结果通过 WebSocket 异步推送给前端。
    public DraftAnalyzeJobSubmitResponse submitAnalyzeJob(MultipartFile file,
                                                          String sessionId,
                                                          String clientIp) throws Exception {
        analysisRequestGuard.validateUpload(file);
        analyzeRateLimitService.checkAnalyzeAllowed(sessionId, clientIp);
        analysisRequestGuard.ensureAsyncCapacity(sessionId);

        byte[] imageBytes = file.getBytes();
        String cacheKey = draftApplicationService.buildAnalyzeCacheKey(sessionId, imageBytes);

        Optional<DraftAnalyzeResponse> cached = draftApplicationService.findCachedAnalyzeResult(cacheKey);
        if (cached.isPresent()) {
            return submitCachedResult(sessionId, cacheKey, cached.get());
        }

        AsyncDraftAnalyzeJob job = analyzeJobStore.createQueued(sessionId, cacheKey);
        analyzeJobUpdateNotifier.notifyJob(job);

        publishOcrJob(job, file, imageBytes);
        meterRegistry.counter("arena.analyze.jobs.submitted").increment();
        return new DraftAnalyzeJobSubmitResponse(
                job.getJobId(),
                job.getStatus().name(),
                "OCR job queued."
        );
    }


    // 如果分析结果已缓存，直接创建一个已完成的分析任务，并推送给前端。这样可以避免重复的 OCR 和分析，提高响应速度。
    private DraftAnalyzeJobSubmitResponse submitCachedResult(String sessionId,
                                                             String cacheKey,
                                                             DraftAnalyzeResponse response) {
        AsyncDraftAnalyzeJob cachedJob = analyzeJobStore.createCompleted(sessionId, cacheKey, response);
        analyzeJobUpdateNotifier.notifyJob(cachedJob);
        return new DraftAnalyzeJobSubmitResponse(
                cachedJob.getJobId(),
                cachedJob.getStatus().name(),
                "Analyze result reused from cache."
        );
    }


    // 给消息队列发一个OCR job信息
    private void publishOcrJob(AsyncDraftAnalyzeJob job,
                               MultipartFile file,
                               byte[] imageBytes) throws Exception {
        try {
            ocrJobPublisher.publish(
                    job.getJobId(),
                    job.getSessionId(),
                    resolveFilename(file),
                    imageBytes
            );
        } catch (Exception ex) {
            job.markFailed("Failed to publish OCR job: " + ex.getMessage());
            analyzeJobStore.save(job);
            meterRegistry.counter("arena.analyze.jobs.failed", "stage", "publish").increment();
            analyzeJobUpdateNotifier.notifyJob(job);
            throw ex;
        }
    }


    // 根据 jobId 获取分析任务的当前状态和结果（如果已完成）。前端会定期轮询这个接口，或者通过 WebSocket 订阅状态更新。
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

            // 判断任务是否是终态，如果已经完成或失败了，说明之前已经处理过这个 OCR 结果了，可能是重复消息或者重试消息，直接忽略即可。
            if (isTerminal(job)) {
                log.info("Ignored duplicate OCR result for terminal jobId={} status={}", job.getJobId(), job.getStatus());
                meterRegistry.counter("arena.ocr.results.ignored", "reason", "terminal-job").increment();
                return;
            }

            if (!message.isSuccess()) {
                failJob(job, resolveError(message.getErrorMessage()), "ocr");
                return;
            }

            if (message.getResultJson() == null || message.getResultJson().isBlank()) {
                failJob(job, "OCR worker returned an empty result.", "ocr-empty");
                return;
            }

            analyzeCompletedOcr(job, message.getResultJson());
        } finally {
            sample.stop(meterRegistry.timer("arena.analyze.jobs.handle_ocr_result"));
        }
    }


   // OCR 结果处理逻辑：先把任务状态从 QUEUED 更新为 ANALYZING，推送状态更新给前端；
   // 然后调用 DraftApplicationService 的 analyzeOcrResult 方法执行分析；
   // 最后根据分析结果更新任务状态为 COMPLETED 或 FAILED，并推送更新。
    private void analyzeCompletedOcr(AsyncDraftAnalyzeJob job, String resultJson) {
        markAnalyzing(job);
        try {
            DraftAnalyzeResponse response = draftApplicationService.analyzeOcrResult(
                    resultJson,
                    job.getSessionId(),
                    job.getCacheKey()
            );
            completeJob(job, response);
        } catch (Exception ex) {
            log.warn("Async analyze job {} failed after OCR completed", job.getJobId(), ex);
            failJob(job, "Analyze failed after OCR completed: " + ex.getMessage(), "analysis");
        }
    }

    private void markAnalyzing(AsyncDraftAnalyzeJob job) {
        // QUEUED 表示 OCR 仍在排队；ANALYZING 表示 OCR 已完成，正在执行 LLM / 规则排序。
        job.markAnalyzing();
        analyzeJobStore.save(job);
        analyzeJobUpdateNotifier.notifyJob(job);
    }

    private void completeJob(AsyncDraftAnalyzeJob job, DraftAnalyzeResponse response) {
        job.markCompleted(response);
        analyzeJobStore.save(job);
        meterRegistry.counter("arena.analyze.jobs.completed").increment();
        analyzeJobUpdateNotifier.notifyJob(job);
        log.info("Async analyze job {} completed", job.getJobId());
    }

    private void failJob(AsyncDraftAnalyzeJob job, String message, String stage) {
        job.markFailed(message);
        analyzeJobStore.save(job);
        meterRegistry.counter("arena.analyze.jobs.failed", "stage", stage).increment();
        analyzeJobUpdateNotifier.notifyJob(job);
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

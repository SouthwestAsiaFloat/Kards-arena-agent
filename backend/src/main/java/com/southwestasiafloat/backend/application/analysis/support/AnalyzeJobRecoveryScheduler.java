package com.southwestasiafloat.backend.application.analysis.support;

/**
 * 分析任务恢复调度器，负责找回超时或卡住的异步任务。
 */

import com.southwestasiafloat.backend.application.analysis.model.AsyncDraftAnalyzeJob;
import com.southwestasiafloat.backend.config.ArenaOcrAsyncProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AnalyzeJobRecoveryScheduler {

    private final AnalyzeJobStore analyzeJobStore;
    private final AnalyzeJobUpdateNotifier analyzeJobUpdateNotifier;
    private final ArenaOcrAsyncProperties ocrAsyncProperties;

    public AnalyzeJobRecoveryScheduler(AnalyzeJobStore analyzeJobStore,
                                       AnalyzeJobUpdateNotifier analyzeJobUpdateNotifier,
                                       ArenaOcrAsyncProperties ocrAsyncProperties) {
        this.analyzeJobStore = analyzeJobStore;
        this.analyzeJobUpdateNotifier = analyzeJobUpdateNotifier;
        this.ocrAsyncProperties = ocrAsyncProperties;
    }

    @Scheduled(fixedDelayString = "${arena.ocr.async.job-recovery-fixed-delay:PT1M}")
    public void failStaleJobs() {
        for (AsyncDraftAnalyzeJob job : analyzeJobStore.findStaleActiveJobs(ocrAsyncProperties.getJobRecoverAfter())) {
            job.markFailed("Analyze job timed out before completion.");
            analyzeJobStore.save(job);
            analyzeJobUpdateNotifier.notifyJob(job);
            log.warn("Marked stale analyze job {} as failed", job.getJobId());
        }
    }
}

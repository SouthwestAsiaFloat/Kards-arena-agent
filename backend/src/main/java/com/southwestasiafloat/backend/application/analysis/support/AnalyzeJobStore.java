package com.southwestasiafloat.backend.application.analysis.support;

/**
 * 分析任务存储门面，负责创建、查询和统计异步任务。
 */

import com.southwestasiafloat.backend.application.analysis.model.AnalyzeJobStatus;
import com.southwestasiafloat.backend.application.analysis.model.AsyncDraftAnalyzeJob;
import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AnalyzeJobStore {

    private final AnalyzeJobRepository repository;

    public AnalyzeJobStore(AnalyzeJobRepository repository) {
        this.repository = repository;
    }

    public AsyncDraftAnalyzeJob createQueued(String sessionId, String cacheKey) {
        String jobId = UUID.randomUUID().toString();
        AsyncDraftAnalyzeJob job = new AsyncDraftAnalyzeJob(jobId, sessionId, cacheKey, AnalyzeJobStatus.QUEUED);
        save(job);
        return job;
    }

    public AsyncDraftAnalyzeJob createCompleted(String sessionId,
                                                String cacheKey,
                                                DraftAnalyzeResponse response) {
        AsyncDraftAnalyzeJob job = createQueued(sessionId, cacheKey);
        job.markCompleted(response);
        save(job);
        return job;
    }

    public Optional<AsyncDraftAnalyzeJob> findById(String jobId) {
        return repository.findById(jobId);
    }

    public AsyncDraftAnalyzeJob require(String jobId) {
        return findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown analyze job: " + jobId));
    }

    public long countActiveJobs() {
        // 这里特意放在 store API 后面，方便后续仓储实现把扫描改成计数器或有序集合，而无需改调用方。
        return repository.findAll().stream()
                .filter(this::isActive)
                .count();
    }

    public long countActiveJobsForSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }
        return repository.findAll().stream()
                .filter(this::isActive)
                .filter(job -> sessionId.equals(job.getSessionId()))
                .count();
    }

    public List<AsyncDraftAnalyzeJob> findStaleActiveJobs(Duration staleAfter) {
        if (staleAfter == null || staleAfter.isZero() || staleAfter.isNegative()) {
            return List.of();
        }

        Instant cutoff = Instant.now().minus(staleAfter);
        return repository.findAll().stream()
                .filter(this::isActive)
                .filter(job -> job.getUpdatedAt() != null && job.getUpdatedAt().isBefore(cutoff))
                .toList();
    }

    public void save(AsyncDraftAnalyzeJob job) {
        repository.save(job);
    }

    private boolean isActive(AsyncDraftAnalyzeJob job) {
        return job != null
                && (job.getStatus() == AnalyzeJobStatus.QUEUED
                || job.getStatus() == AnalyzeJobStatus.ANALYZING);
    }
}

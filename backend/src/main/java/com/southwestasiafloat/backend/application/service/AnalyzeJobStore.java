package com.southwestasiafloat.backend.application.service;

import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class AnalyzeJobStore {

    private final ConcurrentMap<String, AsyncDraftAnalyzeJob> jobs = new ConcurrentHashMap<>();

    public AsyncDraftAnalyzeJob createQueued(String sessionId, String cacheKey) {
        String jobId = UUID.randomUUID().toString();
        AsyncDraftAnalyzeJob job = new AsyncDraftAnalyzeJob(jobId, sessionId, cacheKey, AnalyzeJobStatus.QUEUED);
        jobs.put(jobId, job);
        return job;
    }

    public AsyncDraftAnalyzeJob createCompleted(String sessionId,
                                                String cacheKey,
                                                DraftAnalyzeResponse response) {
        AsyncDraftAnalyzeJob job = createQueued(sessionId, cacheKey);
        job.markCompleted(response);
        return job;
    }

    public Optional<AsyncDraftAnalyzeJob> findById(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public AsyncDraftAnalyzeJob require(String jobId) {
        return findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown analyze job: " + jobId));
    }
}

package com.southwestasiafloat.backend.application.service;

import com.southwestasiafloat.backend.dto.response.DraftAnalyzeResponse;

import java.time.Instant;

public class AsyncDraftAnalyzeJob {

    private final String jobId;
    private final String sessionId;
    private final String cacheKey;
    private final Instant createdAt;
    private volatile Instant updatedAt;
    private volatile AnalyzeJobStatus status;
    private volatile DraftAnalyzeResponse result;
    private volatile String errorMessage;

    public AsyncDraftAnalyzeJob(String jobId, String sessionId, String cacheKey, AnalyzeJobStatus status) {
        this.jobId = jobId;
        this.sessionId = sessionId;
        this.cacheKey = cacheKey;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getJobId() {
        return jobId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public AnalyzeJobStatus getStatus() {
        return status;
    }

    public DraftAnalyzeResponse getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void markAnalyzing() {
        this.status = AnalyzeJobStatus.ANALYZING;
        this.updatedAt = Instant.now();
    }

    public void markCompleted(DraftAnalyzeResponse result) {
        this.result = result;
        this.errorMessage = null;
        this.status = AnalyzeJobStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = AnalyzeJobStatus.FAILED;
        this.updatedAt = Instant.now();
    }
}

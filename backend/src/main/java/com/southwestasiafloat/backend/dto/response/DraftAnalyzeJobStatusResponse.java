package com.southwestasiafloat.backend.dto.response;

import com.southwestasiafloat.backend.application.service.AsyncDraftAnalyzeJob;

import java.time.Instant;

public class DraftAnalyzeJobStatusResponse {

    private String jobId;
    private String sessionId;
    private String status;
    private String errorMessage;
    private DraftAnalyzeResponse result;
    private Instant createdAt;
    private Instant updatedAt;

    public DraftAnalyzeJobStatusResponse() {
    }

    public static DraftAnalyzeJobStatusResponse from(AsyncDraftAnalyzeJob job) {
        DraftAnalyzeJobStatusResponse response = new DraftAnalyzeJobStatusResponse();
        response.setJobId(job.getJobId());
        response.setSessionId(job.getSessionId());
        response.setStatus(job.getStatus().name());
        response.setErrorMessage(job.getErrorMessage());
        response.setResult(job.getResult());
        response.setCreatedAt(job.getCreatedAt());
        response.setUpdatedAt(job.getUpdatedAt());
        return response;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public DraftAnalyzeResponse getResult() {
        return result;
    }

    public void setResult(DraftAnalyzeResponse result) {
        this.result = result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

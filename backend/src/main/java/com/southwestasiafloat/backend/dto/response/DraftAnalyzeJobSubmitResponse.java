package com.southwestasiafloat.backend.dto.response;

public class DraftAnalyzeJobSubmitResponse {

    private String jobId;
    private String status;
    private String message;

    public DraftAnalyzeJobSubmitResponse() {
    }

    public DraftAnalyzeJobSubmitResponse(String jobId, String status, String message) {
        this.jobId = jobId;
        this.status = status;
        this.message = message;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

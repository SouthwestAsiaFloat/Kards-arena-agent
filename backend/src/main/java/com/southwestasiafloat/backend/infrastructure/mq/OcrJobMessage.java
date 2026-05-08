package com.southwestasiafloat.backend.infrastructure.mq;

/**
 * OCR 任务消息体，描述待处理的 OCR 请求。
 */

public class OcrJobMessage {

    private String jobId;
    private String sessionId;
    private String filename;
    private String imageBase64;

    public OcrJobMessage() {
    }

    public OcrJobMessage(String jobId, String sessionId, String filename, String imageBase64) {
        this.jobId = jobId;
        this.sessionId = sessionId;
        this.filename = filename;
        this.imageBase64 = imageBase64;
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

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }
}

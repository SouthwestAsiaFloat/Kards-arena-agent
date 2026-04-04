package com.southwestasiafloat.backend.dto.response;

public class StartDraftResponse {

    private String sessionId;
    private String message;

    public StartDraftResponse() {
    }

    public StartDraftResponse(String sessionId, String message) {
        this.sessionId = sessionId;
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

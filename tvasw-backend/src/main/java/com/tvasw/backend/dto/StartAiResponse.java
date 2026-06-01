package com.tvasw.backend.dto;

public class StartAiResponse {
    private String sessionId;
    private String message;

    public StartAiResponse() {}
    public StartAiResponse(String sessionId, String message) { this.sessionId = sessionId; this.message = message; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

package com.tvasw.backend.dto;

import jakarta.validation.constraints.*;

public class AiMessageRequest {
    private Long projectId;
    @NotBlank @Size(max = 100)
    private String sessionId;
    @NotBlank @Size(max = 4000)
    private String message;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

package com.tvasw.backend.dto;

public class StartAiRequest {
    private Long projectId;
    private String ideaText;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getIdeaText() { return ideaText; }
    public void setIdeaText(String ideaText) { this.ideaText = ideaText; }
}

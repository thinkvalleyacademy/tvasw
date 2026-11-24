package com.tvasw.backend.dto;

import jakarta.validation.constraints.*;

public class StartAiRequest {
    @NotNull
    private Long projectId;
    @NotBlank @Size(max = 4000)
    private String ideaText;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getIdeaText() { return ideaText; }
    public void setIdeaText(String ideaText) { this.ideaText = ideaText; }
}

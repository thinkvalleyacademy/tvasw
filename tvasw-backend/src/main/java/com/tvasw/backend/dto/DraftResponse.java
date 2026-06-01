package com.tvasw.backend.dto;

public class DraftResponse {
    private Long projectId;
    private String prd;
    private String srs;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getPrd() { return prd; }
    public void setPrd(String prd) { this.prd = prd; }
    public String getSrs() { return srs; }
    public void setSrs(String srs) { this.srs = srs; }
}

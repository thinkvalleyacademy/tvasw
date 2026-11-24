package com.tvasw.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "projects")
public class Project {

    public static final String CREATED = "CREATED";
    public static final String SUBMITTED = "SUBMITTED";

    // From this progress on, the interview is finished and the document can be created
    public static final int READY_PROGRESS = 90;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String industry;

    @Column(length = 2000)
    private String description;

    private String status;

    private Long ownerId;

    private Integer progress;

    @Column(length = 100000)
    private String prd;

    @Column(length = 100000)
    private String srs;

    // Last chat message included in the draft
    private Long draftMessageId;

    private Instant submittedAt;

    private Instant createdAt = Instant.now();

    public Project() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public int getProgress() {
        return progress == null ? 0 : progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getPrd() {
        return prd;
    }

    public void setPrd(String prd) {
        this.prd = prd;
    }

    public String getSrs() {
        return srs;
    }

    public void setSrs(String srs) {
        this.srs = srs;
    }

    public Long getDraftMessageId() {
        return draftMessageId;
    }

    public void setDraftMessageId(Long draftMessageId) {
        this.draftMessageId = draftMessageId;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

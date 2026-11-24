package com.tvasw.backend.dto;

import com.tvasw.backend.model.Project;
import com.tvasw.backend.model.User;

import java.time.Instant;

public record ProjectView(Long id, String name, String industry, String status, String stage, int progress,
                          String clientName, String clientEmail, Instant createdAt, Instant submittedAt) {
    public static ProjectView of(Project p, User owner, String stage) {
        return new ProjectView(p.getId(), p.getName(), p.getIndustry(), p.getStatus(), stage, p.getProgress(),
            owner == null ? null : owner.getName(), owner == null ? null : owner.getEmail(),
            p.getCreatedAt(), p.getSubmittedAt());
    }
}

package com.tvasw.backend.dto;

import java.util.List;

public record ProjectDetail(ProjectView project, String description, List<MessageView> messages,
                            String prd, String srs, boolean draftOutdated) {
}

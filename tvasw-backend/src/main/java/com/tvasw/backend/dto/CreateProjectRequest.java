package com.tvasw.backend.dto;

import jakarta.validation.constraints.*;

public class CreateProjectRequest {
    @NotBlank @Size(max = 255)
    private String name;
    @Size(max = 255)
    private String industry;
    @NotBlank @Size(max = 2000)
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

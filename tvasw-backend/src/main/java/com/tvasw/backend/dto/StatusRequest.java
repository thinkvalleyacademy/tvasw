package com.tvasw.backend.dto;

import jakarta.validation.constraints.*;

public class StatusRequest {
    @NotBlank @Pattern(regexp = "SUBMITTED|IN_REVIEW|QUOTED|ACCEPTED|DECLINED", message = "is not valid")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

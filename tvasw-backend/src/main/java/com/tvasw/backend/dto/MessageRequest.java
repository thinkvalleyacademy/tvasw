package com.tvasw.backend.dto;

import jakarta.validation.constraints.*;

public class MessageRequest {
    @NotBlank @Size(max = 4000)
    private String message;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

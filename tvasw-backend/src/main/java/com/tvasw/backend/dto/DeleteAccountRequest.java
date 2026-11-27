package com.tvasw.backend.dto;

import jakarta.validation.constraints.*;

public class DeleteAccountRequest {
    @NotBlank @Size(max = 72)
    private String password;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}

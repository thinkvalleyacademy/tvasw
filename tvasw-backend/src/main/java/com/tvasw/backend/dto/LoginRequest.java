package com.tvasw.backend.dto;

import jakarta.validation.constraints.*;

public class LoginRequest {
    @NotBlank @Size(max = 255)
    private String email;
    @NotBlank @Size(max = 72)
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}

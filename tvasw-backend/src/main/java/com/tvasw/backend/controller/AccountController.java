package com.tvasw.backend.controller;

import com.tvasw.backend.dto.DeleteAccountRequest;
import com.tvasw.backend.service.AuthService;
import com.tvasw.backend.service.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AuthService authService;
    private final RateLimiter limiter;

    public AccountController(AuthService authService, RateLimiter limiter) {
        this.authService = authService;
        this.limiter = limiter;
    }

    @PostMapping("/delete")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @Valid @RequestBody DeleteAccountRequest req) {
        limiter.check("delete-account:" + userId, 5, Duration.ofMinutes(15), "Too many attempts. Please try again later.");
        authService.deleteAccount(userId, req.getPassword());
        return ResponseEntity.noContent().build();
    }
}

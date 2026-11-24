package com.tvasw.backend.controller;

import com.tvasw.backend.dto.*;
import com.tvasw.backend.service.AuthService;
import com.tvasw.backend.service.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String TOO_MANY = "Too many attempts. Please try again later.";

    private final AuthService authService;
    private final RateLimiter limiter;

    @Value("${tvasw.limits.login-per-15-minutes}")
    private int loginLimit;

    @Value("${tvasw.limits.register-per-hour}")
    private int registerLimit;

    public AuthController(AuthService authService, RateLimiter limiter) {
        this.authService = authService;
        this.limiter = limiter;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        limiter.check("register:" + clientIp(http), registerLimit, Duration.ofHours(1), TOO_MANY);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        Duration period = Duration.ofMinutes(15);
        limiter.check("login-ip:" + clientIp(http), loginLimit * 3, period, TOO_MANY);
        limiter.check("login-email:" + AuthService.normalize(req.getEmail()), loginLimit, period, TOO_MANY);
        return ResponseEntity.ok(authService.login(req));
    }

    // Only the local frontend proxy may supply the client address
    private String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        try {
            if (forwarded != null && InetAddress.getByName(http.getRemoteAddr()).isLoopbackAddress()) {
                return forwarded;
            }
        } catch (UnknownHostException ignored) {
        }
        return http.getRemoteAddr();
    }
}

package com.tvasw.backend.controller;

import com.tvasw.backend.dto.*;
import com.tvasw.backend.service.AiService;
import com.tvasw.backend.service.ProjectService;
import com.tvasw.backend.service.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/ai/projects")
public class AiController {

    private final AiService aiService;
    private final ProjectService projects;
    private final RateLimiter limiter;

    @Value("${tvasw.limits.ai-per-minute}")
    private int perMinute;

    @Value("${tvasw.limits.ai-per-day}")
    private int perDay;

    @Value("${tvasw.limits.ai-global-per-day}")
    private int globalPerDay;

    public AiController(AiService aiService, ProjectService projects, RateLimiter limiter) {
        this.aiService = aiService;
        this.projects = projects;
        this.limiter = limiter;
    }

    @GetMapping
    public List<ProjectView> list(@AuthenticationPrincipal Long userId) {
        return projects.listForClient(userId);
    }

    @PostMapping
    public ResponseEntity<ProjectView> createProject(@AuthenticationPrincipal Long userId, @Valid @RequestBody CreateProjectRequest req) {
        limiter.check("project:" + userId, perMinute, Duration.ofMinutes(1), "Too many projects. Please wait a minute.");
        return ResponseEntity.status(HttpStatus.CREATED).body(projects.create(userId, req));
    }

    @GetMapping("/{id}")
    public ProjectDetail get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return projects.detail(projects.findOwned(userId, id));
    }

    @PostMapping("/{id}/start")
    public ProjectDetail start(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        checkAiLimits(userId, 1);
        return aiService.start(userId, id);
    }

    @PostMapping("/{id}/messages")
    public ProjectDetail message(@AuthenticationPrincipal Long userId, @PathVariable Long id, @Valid @RequestBody MessageRequest req) {
        checkAiLimits(userId, 1);
        return aiService.sendMessage(userId, id, req.getMessage());
    }

    @PostMapping("/{id}/draft")
    public ProjectDetail draft(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        // A draft makes two AI calls
        checkAiLimits(userId, 2);
        return aiService.generateDraft(userId, id);
    }

    @PostMapping("/{id}/submit")
    public ProjectDetail submit(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return projects.submit(userId, id);
    }

    private void checkAiLimits(Long userId, int calls) {
        for (int i = 0; i < calls; i++) {
            limiter.check("ai-minute:" + userId, perMinute, Duration.ofMinutes(1), "Too many AI requests. Please wait a minute.");
            limiter.check("ai-day:" + userId, perDay, Duration.ofDays(1), "Daily AI limit reached. Please try again tomorrow.");
            limiter.check("ai-global", globalPerDay, Duration.ofDays(1), "The AI assistant is at capacity today. Please try again tomorrow.");
        }
    }
}

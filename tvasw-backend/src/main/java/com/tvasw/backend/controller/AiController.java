package com.tvasw.backend.controller;

import com.tvasw.backend.dto.*;
import com.tvasw.backend.model.Project;
import com.tvasw.backend.repository.ProjectRepository;
import com.tvasw.backend.service.AiService;
import com.tvasw.backend.service.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;
    private final ProjectRepository projectRepo;
    private final RateLimiter limiter;

    @Value("${tvasw.limits.ai-per-minute}")
    private int perMinute;

    @Value("${tvasw.limits.ai-per-day}")
    private int perDay;

    @Value("${tvasw.limits.ai-global-per-day}")
    private int globalPerDay;

    public AiController(AiService aiService, ProjectRepository projectRepo, RateLimiter limiter) {
        this.aiService = aiService;
        this.projectRepo = projectRepo;
        this.limiter = limiter;
    }

    @PostMapping("/projects")
    public ResponseEntity<Project> createProject(@AuthenticationPrincipal Long userId, @Valid @RequestBody CreateProjectRequest req) {
        limiter.check("project:" + userId, perMinute, Duration.ofMinutes(1), "Too many projects. Please wait a minute.");
        Project p = new Project();
        p.setName(req.getName());
        p.setIndustry(req.getIndustry());
        p.setDescription(req.getDescription());
        p.setStatus("CREATED");
        p.setOwnerId(userId);
        Project saved = projectRepo.save(p);
        return ResponseEntity.created(URI.create("/api/ai/projects/" + saved.getId())).body(saved);
    }

    @PostMapping("/start")
    public ResponseEntity<StartAiResponse> start(@AuthenticationPrincipal Long userId, @Valid @RequestBody StartAiRequest req) {
        checkAiLimits(userId);
        StartAiResponse resp = aiService.startConversation(userId, req);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/message")
    public ResponseEntity<AiMessageResponse> message(@AuthenticationPrincipal Long userId, @Valid @RequestBody AiMessageRequest req) {
        checkAiLimits(userId);
        AiMessageResponse resp = aiService.sendMessage(userId, req);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/draft/{projectId}")
    public ResponseEntity<DraftResponse> draft(@AuthenticationPrincipal Long userId, @PathVariable Long projectId) {
        // A draft makes two AI calls
        checkAiLimits(userId);
        checkAiLimits(userId);
        DraftResponse d = aiService.generateDraft(userId, projectId);
        return ResponseEntity.ok(d);
    }

    private void checkAiLimits(Long userId) {
        limiter.check("ai-minute:" + userId, perMinute, Duration.ofMinutes(1), "Too many AI requests. Please wait a minute.");
        limiter.check("ai-day:" + userId, perDay, Duration.ofDays(1), "Daily AI limit reached. Please try again tomorrow.");
        limiter.check("ai-global", globalPerDay, Duration.ofDays(1), "The AI assistant is at capacity today. Please try again tomorrow.");
    }
}

package com.tvasw.backend.controller;

import com.tvasw.backend.dto.*;
import com.tvasw.backend.model.Project;
import com.tvasw.backend.repository.ProjectRepository;
import com.tvasw.backend.service.AiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;
    private final ProjectRepository projectRepo;

    public AiController(AiService aiService, ProjectRepository projectRepo) {
        this.aiService = aiService;
        this.projectRepo = projectRepo;
    }

    @PostMapping("/projects")
    public ResponseEntity<Project> createProject(@RequestBody CreateProjectRequest req) {
        Project p = new Project();
        p.setName(req.getName());
        p.setIndustry(req.getIndustry());
        p.setDescription(req.getDescription());
        p.setStatus("CREATED");
        Project saved = projectRepo.save(p);
        return ResponseEntity.created(URI.create("/api/ai/projects/" + saved.getId())).body(saved);
    }

    @PostMapping("/start")
    public ResponseEntity<StartAiResponse> start(@RequestBody StartAiRequest req) {
        StartAiResponse resp = aiService.startConversation(req);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/message")
    public ResponseEntity<AiMessageResponse> message(@RequestBody AiMessageRequest req) {
        AiMessageResponse resp = aiService.sendMessage(req);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/draft/{projectId}")
    public ResponseEntity<DraftResponse> draft(@PathVariable Long projectId) {
        DraftResponse d = aiService.generateDraft(projectId);
        return ResponseEntity.ok(d);
    }
}

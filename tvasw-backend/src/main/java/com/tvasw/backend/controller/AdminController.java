package com.tvasw.backend.controller;

import com.tvasw.backend.dto.*;
import com.tvasw.backend.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Reached only through the frontend's /admin routes, see AdminTokenFilter
@RestController
@RequestMapping("/api/internal/projects")
public class AdminController {

    private final ProjectService projects;

    public AdminController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    public List<ProjectView> list() {
        return projects.listSubmitted();
    }

    @GetMapping("/{id}")
    public ProjectDetail get(@PathVariable Long id) {
        return projects.detail(projects.findSubmitted(id));
    }

    @PostMapping("/{id}/status")
    public ProjectDetail status(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return projects.updateStatus(id, req.getStatus());
    }
}

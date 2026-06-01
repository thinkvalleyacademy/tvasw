package com.tvasw.backend.service;

import com.tvasw.backend.dto.*;
import com.tvasw.backend.model.Project;
import com.tvasw.backend.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiService {

    private final GeminiClient gemini;
    private final ProjectRepository projectRepo;

    // in-memory session store for Phase 1
    private final Map<String, List<String>> sessions = new HashMap<>();

    public AiService(GeminiClient gemini, ProjectRepository projectRepo) {
        this.gemini = gemini;
        this.projectRepo = projectRepo;
    }

    public StartAiResponse startConversation(StartAiRequest req) {
        Optional<Project> p = projectRepo.findById(req.getProjectId());
        String sessionId = UUID.randomUUID().toString();
        String prompt = "Client idea: " + req.getIdeaText() + "\nAsk clarifying questions to build a software requirements document.";
        String reply = gemini.generate(prompt);
        sessions.put(sessionId, new ArrayList<>(List.of(req.getIdeaText(), reply)));
        return new StartAiResponse(sessionId, reply);
    }

    public AiMessageResponse sendMessage(AiMessageRequest req) {
        String sessionId = req.getSessionId();
        List<String> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(req.getMessage());
        String prompt = String.join("\n", history) + "\nProvide next clarification or consolidation.";
        String reply = gemini.generate(prompt);
        history.add(reply);
        return new AiMessageResponse(sessionId, reply);
    }

    public DraftResponse generateDraft(Long projectId) {
        Optional<Project> p = projectRepo.findById(projectId);
        DraftResponse d = new DraftResponse();
        d.setProjectId(projectId);
        String base = p.map(Project::getDescription).orElse("No project description provided.");
        String prd = gemini.generate("Generate a brief PRD from:\n" + base);
        String srs = gemini.generate("Generate a brief SRS from:\n" + base);
        d.setPrd(prd);
        d.setSrs(srs);
        return d;
    }
}

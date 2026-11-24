package com.tvasw.backend.service;

import com.tvasw.backend.dto.*;
import com.tvasw.backend.model.ChatMessage;
import com.tvasw.backend.model.Project;
import com.tvasw.backend.repository.ChatMessageRepository;
import com.tvasw.backend.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiService {

    // Keeps token cost bounded; the project description is always included separately
    private static final int MAX_HISTORY_CHARS = 30000;

    private final GeminiClient gemini;
    private final ProjectRepository projectRepo;
    private final ChatMessageRepository chatRepo;

    public AiService(GeminiClient gemini, ProjectRepository projectRepo, ChatMessageRepository chatRepo) {
        this.gemini = gemini;
        this.projectRepo = projectRepo;
        this.chatRepo = chatRepo;
    }

    public StartAiResponse startConversation(Long userId, StartAiRequest req) {
        Project p = findProject(userId, req.getProjectId());
        String sessionId = UUID.randomUUID().toString();
        String prompt = "Project: " + p.getDescription() + "\nClient idea: " + req.getIdeaText()
            + "\nAsk clarifying questions to build a software requirements document.";
        String reply = gemini.generate(prompt);
        saveExchange(sessionId, p.getId(), req.getIdeaText(), reply);
        return new StartAiResponse(sessionId, reply);
    }

    public AiMessageResponse sendMessage(Long userId, AiMessageRequest req) {
        String sessionId = req.getSessionId();
        List<ChatMessage> history = chatRepo.findBySessionIdOrderById(sessionId);
        if (history.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        Project p = findProject(userId, history.get(0).getProjectId());
        String prompt = "Project: " + p.getDescription()
            + "\n\nConversation so far:\n" + transcript(history)
            + "\n\nClient: " + req.getMessage()
            + "\n\nProvide next clarification or consolidation.";
        String reply = gemini.generate(prompt);
        saveExchange(sessionId, p.getId(), req.getMessage(), reply);
        return new AiMessageResponse(sessionId, reply);
    }

    public DraftResponse generateDraft(Long userId, Long projectId) {
        Project p = findProject(userId, projectId);
        String base = "Project: " + p.getDescription()
            + "\n\nRequirement conversation:\n" + transcript(chatRepo.findByProjectIdOrderById(projectId));
        String rules = " from the project and conversation below. The client's later answers override earlier ones."
            + " Use the client's stated facts; list anything unanswered as an open question.\n\n";
        DraftResponse d = new DraftResponse();
        d.setProjectId(projectId);
        d.setPrd(gemini.generate("Generate a brief PRD" + rules + base));
        d.setSrs(gemini.generate("Generate a brief SRS" + rules + base));
        return d;
    }

    private Project findProject(Long userId, Long projectId) {
        return projectRepo.findByIdAndOwnerId(projectId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private void saveExchange(String sessionId, Long projectId, String message, String reply) {
        chatRepo.saveAll(List.of(
            new ChatMessage(sessionId, projectId, ChatMessage.CLIENT, message),
            new ChatMessage(sessionId, projectId, ChatMessage.ASSISTANT, reply)));
    }

    private String transcript(List<ChatMessage> messages) {
        String text = messages.stream()
            .map(m -> m.getAuthor() + ": " + m.getContent())
            .collect(Collectors.joining("\n\n"));
        return text.length() <= MAX_HISTORY_CHARS ? text : text.substring(text.length() - MAX_HISTORY_CHARS);
    }
}

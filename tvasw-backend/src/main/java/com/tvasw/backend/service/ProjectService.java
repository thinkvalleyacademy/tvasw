package com.tvasw.backend.service;

import com.tvasw.backend.dto.*;
import com.tvasw.backend.model.ChatMessage;
import com.tvasw.backend.model.Project;
import com.tvasw.backend.model.User;
import com.tvasw.backend.repository.ChatMessageRepository;
import com.tvasw.backend.repository.ProjectRepository;
import com.tvasw.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepo;
    private final ChatMessageRepository chatRepo;
    private final UserRepository userRepo;

    public ProjectService(ProjectRepository projectRepo, ChatMessageRepository chatRepo, UserRepository userRepo) {
        this.projectRepo = projectRepo;
        this.chatRepo = chatRepo;
        this.userRepo = userRepo;
    }

    public ProjectView create(Long userId, CreateProjectRequest req) {
        Project p = new Project();
        p.setName(req.getName());
        p.setIndustry(req.getIndustry());
        p.setDescription(req.getDescription());
        p.setStatus(Project.CREATED);
        p.setOwnerId(userId);
        return view(projectRepo.save(p), null);
    }

    public List<ProjectView> listForClient(Long userId) {
        return projectRepo.findByOwnerIdOrderByIdDesc(userId).stream().map(p -> view(p, null)).toList();
    }

    public List<ProjectView> listSubmitted() {
        List<Project> found = projectRepo.findBySubmittedAtNotNullOrderBySubmittedAtDesc();
        Map<Long, User> owners = userRepo.findAllById(found.stream().map(Project::getOwnerId).distinct().toList())
            .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return found.stream().map(p -> view(p, owners.get(p.getOwnerId()))).toList();
    }

    public Project findOwned(Long userId, Long id) {
        return projectRepo.findByIdAndOwnerId(id, userId).orElseThrow(this::notFound);
    }

    public Project findSubmitted(Long id) {
        return projectRepo.findById(id).filter(p -> p.getSubmittedAt() != null).orElseThrow(this::notFound);
    }

    public Project findEditable(Long userId, Long id) {
        Project p = findOwned(userId, id);
        if (p.getSubmittedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This project has already been sent to Thinkvalley");
        }
        return p;
    }

    public ProjectDetail submit(Long userId, Long id) {
        Project p = findEditable(userId, id);
        if (p.getPrd() == null || isDraftOutdated(p)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Please generate an up-to-date draft first");
        }
        p.setStatus(Project.SUBMITTED);
        p.setSubmittedAt(Instant.now());
        return detail(projectRepo.save(p));
    }

    public ProjectDetail updateStatus(Long id, String status) {
        Project p = findSubmitted(id);
        p.setStatus(status);
        return detail(projectRepo.save(p));
    }

    public ProjectDetail detail(Project p) {
        List<ChatMessage> messages = chatRepo.findByProjectIdOrderById(p.getId());
        User owner = p.getOwnerId() == null ? null : userRepo.findById(p.getOwnerId()).orElse(null);
        return new ProjectDetail(view(p, owner), p.getDescription(),
            messages.stream().map(MessageView::of).toList(), p.getPrd(), p.getSrs(), isDraftOutdated(p));
    }

    private ProjectView view(Project p, User owner) {
        return ProjectView.of(p, owner, stage(p));
    }

    // What the client sees; follows the status once the project has been sent
    private String stage(Project p) {
        if (p.getSubmittedAt() != null) return p.getStatus();
        if (p.getPrd() != null) return isDraftOutdated(p) ? "DOCUMENT_OUTDATED" : "DOCUMENT_READY";
        return p.getProgress() >= Project.READY_PROGRESS ? "READY" : "GATHERING";
    }

    // Compares message ids, so a message sent while a draft was being generated also counts
    private boolean isDraftOutdated(Project p) {
        if (p.getPrd() == null) return false;
        long covered = p.getDraftMessageId() == null ? 0 : p.getDraftMessageId();
        return chatRepo.findTopByProjectIdOrderByIdDesc(p.getId()).map(m -> m.getId() > covered).orElse(false);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
    }
}

package com.tvasw.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_chat_session", columnList = "sessionId"),
    @Index(name = "idx_chat_project", columnList = "projectId")
})
public class ChatMessage {

    public static final String CLIENT = "Client";
    public static final String ASSISTANT = "Assistant";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false, length = 100000)
    private String content;

    // Quick replies offered with an assistant message, one per line
    @Column(length = 1000)
    private String options;

    private Instant createdAt = Instant.now();

    public ChatMessage() {}

    public ChatMessage(String sessionId, Long projectId, String author, String content) {
        this.sessionId = sessionId;
        this.projectId = projectId;
        this.author = author;
        this.content = content;
    }

    public Long getId() { return id; }
    public String getSessionId() { return sessionId; }
    public Long getProjectId() { return projectId; }
    public String getAuthor() { return author; }
    public String getContent() { return content; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
    public Instant getCreatedAt() { return createdAt; }
}

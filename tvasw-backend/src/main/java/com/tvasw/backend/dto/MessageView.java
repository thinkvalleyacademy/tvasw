package com.tvasw.backend.dto;

import com.tvasw.backend.model.ChatMessage;

import java.time.Instant;
import java.util.List;

public record MessageView(String author, String content, List<String> options, Instant createdAt) {
    public static MessageView of(ChatMessage m) {
        List<String> options = m.getOptions() == null || m.getOptions().isBlank() ? List.of() : m.getOptions().lines().toList();
        return new MessageView(m.getAuthor(), m.getContent(), options, m.getCreatedAt());
    }
}

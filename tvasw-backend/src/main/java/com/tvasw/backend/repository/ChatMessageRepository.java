package com.tvasw.backend.repository;

import com.tvasw.backend.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findTopByProjectIdOrderByIdDesc(Long projectId);
    List<ChatMessage> findByProjectIdOrderById(Long projectId);
}

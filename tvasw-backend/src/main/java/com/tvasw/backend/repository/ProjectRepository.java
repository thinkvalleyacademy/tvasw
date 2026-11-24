package com.tvasw.backend.repository;

import com.tvasw.backend.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);
}

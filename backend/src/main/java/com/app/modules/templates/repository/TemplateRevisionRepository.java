package com.app.modules.templates.repository;

import com.app.modules.templates.entity.TemplateRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateRevisionRepository extends JpaRepository<TemplateRevision, UUID> {
    List<TemplateRevision> findByTemplate_IdOrderByVersionDesc(UUID templateId);

    Optional<TemplateRevision> findTopByTemplate_IdOrderByVersionDesc(UUID templateId);
}


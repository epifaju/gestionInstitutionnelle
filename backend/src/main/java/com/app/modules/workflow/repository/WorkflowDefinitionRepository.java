package com.app.modules.workflow.repository;

import com.app.modules.workflow.entity.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {
    Optional<WorkflowDefinition> findByOrganisationIdAndProcessKey(UUID organisationId, String processKey);
}


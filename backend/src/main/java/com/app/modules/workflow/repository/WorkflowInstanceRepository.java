package com.app.modules.workflow.repository;

import com.app.modules.workflow.entity.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {
    Optional<WorkflowInstance> findByOrganisationIdAndProcessKeyAndSubjectTypeAndSubjectId(
            UUID organisationId, String processKey, String subjectType, UUID subjectId);

    List<WorkflowInstance> findByOrganisationIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
            UUID organisationId, String subjectType, UUID subjectId);
}


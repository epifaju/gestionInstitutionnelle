package com.app.modules.workflow.repository;

import com.app.modules.workflow.entity.WorkflowAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowActionRepository extends JpaRepository<WorkflowAction, UUID> {
    List<WorkflowAction> findByInstance_IdOrderByCreatedAtAsc(UUID instanceId);
}


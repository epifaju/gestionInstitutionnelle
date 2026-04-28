package com.app.modules.workflow.repository;

import com.app.modules.workflow.entity.WorkflowRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowRuleRepository extends JpaRepository<WorkflowRule, UUID> {
    List<WorkflowRule> findByDefinition_IdOrderByCreatedAtAsc(UUID definitionId);
}


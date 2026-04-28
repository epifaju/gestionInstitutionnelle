package com.app.modules.workflow.dto;

import com.app.modules.workflow.entity.WorkflowDecision;

import java.time.Instant;
import java.util.UUID;

public record WorkflowActionDto(
        UUID id,
        int level,
        UUID actorUserId,
        String actorRole,
        WorkflowDecision decision,
        String comment,
        Instant createdAt
) {}


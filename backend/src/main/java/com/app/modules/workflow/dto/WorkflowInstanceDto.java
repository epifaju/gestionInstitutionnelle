package com.app.modules.workflow.dto;

import com.app.modules.workflow.entity.WorkflowInstanceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowInstanceDto(
        UUID id,
        String processKey,
        String subjectType,
        UUID subjectId,
        BigDecimal amountEur,
        WorkflowInstanceStatus status,
        int currentLevel,
        Instant createdAt,
        Instant completedAt,
        List<WorkflowActionDto> actions
) {}


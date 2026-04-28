package com.app.modules.workflow.dto;

import java.util.List;
import java.util.UUID;

public record WorkflowDefinitionDto(
        UUID id,
        String processKey,
        boolean enabled,
        List<WorkflowRuleDto> rules
) {}


package com.app.modules.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpsertWorkflowDefinitionRequest(
        @NotBlank String processKey,
        @NotNull Boolean enabled,
        List<UpsertWorkflowRuleRequest> rules
) {}


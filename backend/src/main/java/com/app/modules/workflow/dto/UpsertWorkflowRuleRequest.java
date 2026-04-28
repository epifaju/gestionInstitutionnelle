package com.app.modules.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpsertWorkflowRuleRequest(
        BigDecimal minAmountEur,
        BigDecimal maxAmountEur,
        @NotNull Integer levels,
        @NotBlank String level1Role,
        String level2Role
) {}


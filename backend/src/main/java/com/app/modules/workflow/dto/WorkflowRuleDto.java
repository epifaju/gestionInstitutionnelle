package com.app.modules.workflow.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkflowRuleDto(
        UUID id,
        BigDecimal minAmountEur,
        BigDecimal maxAmountEur,
        int levels,
        String level1Role,
        String level2Role
) {}


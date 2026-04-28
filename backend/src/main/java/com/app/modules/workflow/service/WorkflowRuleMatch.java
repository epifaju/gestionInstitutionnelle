package com.app.modules.workflow.service;

import java.math.BigDecimal;

public record WorkflowRuleMatch(
        int levels,
        String level1Role,
        String level2Role,
        BigDecimal minAmountEur,
        BigDecimal maxAmountEur
) {}


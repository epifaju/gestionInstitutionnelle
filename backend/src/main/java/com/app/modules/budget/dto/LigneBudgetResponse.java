package com.app.modules.budget.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LigneBudgetResponse(
        UUID id,
        UUID categorieId,
        String categorieLibelle,
        String type,
        BigDecimal montantPrevu,
        BigDecimal montantRealise,
        BigDecimal ecart,
        BigDecimal tauxExecutionPct,
        boolean alerteDepassement) {}

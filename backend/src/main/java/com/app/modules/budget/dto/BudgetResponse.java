package com.app.modules.budget.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        int annee,
        String statut,
        LocalDateTime dateValidation,
        List<LigneBudgetResponse> lignes,
        BigDecimal totalDepensesPrevu,
        BigDecimal totalDepensesRealise,
        BigDecimal totalRecettesPrevu,
        BigDecimal totalRecettesRealise) {}

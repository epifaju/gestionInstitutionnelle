package com.app.modules.budget.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BudgetRequest(
        @NotNull @Min(2020) Integer annee,
        @NotEmpty List<LigneBudgetRequest> lignes,
        String notes) {}

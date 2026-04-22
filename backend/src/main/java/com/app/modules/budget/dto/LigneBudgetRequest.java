package com.app.modules.budget.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record LigneBudgetRequest(
        @NotNull UUID categorieId,
        @NotNull String type,
        @NotNull @PositiveOrZero BigDecimal montantPrevu) {}

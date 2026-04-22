package com.app.modules.budget.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ModifierLigneBudgetRequest(@NotNull @PositiveOrZero BigDecimal montantPrevu) {}

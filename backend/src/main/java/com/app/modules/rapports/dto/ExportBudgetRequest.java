package com.app.modules.rapports.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExportBudgetRequest(@NotNull @Min(2020) Integer annee) {}


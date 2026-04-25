package com.app.modules.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayrollLegalConstantRequest(
        @NotBlank String code,
        @NotBlank String libelle,
        @NotNull @Positive BigDecimal valeur,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}


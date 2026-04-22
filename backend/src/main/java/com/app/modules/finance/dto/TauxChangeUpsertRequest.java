package com.app.modules.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TauxChangeUpsertRequest(
        @NotNull LocalDate date,
        @NotBlank String devise,
        @NotNull @DecimalMin(value = "0.000001") BigDecimal tauxVersEur
) {}


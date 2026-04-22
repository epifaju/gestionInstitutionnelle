package com.app.modules.rh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GrilleSalarialeRequest(
        @NotNull @Positive BigDecimal brut,
        @NotNull @Positive BigDecimal net,
        @NotBlank String devise,
        @NotNull LocalDate dateDebut
) {}

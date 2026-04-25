package com.app.modules.missions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FraisRequest(
        @NotBlank String typeFrais,
        @NotBlank String description,
        @NotNull LocalDate dateFrais,
        @NotNull @Positive BigDecimal montant,
        @NotBlank String devise
) {}


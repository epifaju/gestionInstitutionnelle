package com.app.modules.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecetteRequest(
        @NotNull @PastOrPresent LocalDate dateRecette,
        @NotNull @Positive BigDecimal montant,
        @NotBlank String devise,
        @NotBlank String typeRecette,
        String description,
        String modeEncaissement,
        UUID categorieId
) {}

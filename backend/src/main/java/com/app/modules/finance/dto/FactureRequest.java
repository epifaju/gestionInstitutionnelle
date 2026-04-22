package com.app.modules.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FactureRequest(
        @NotBlank String fournisseur,
        @NotNull @PastOrPresent LocalDate dateFacture,
        @NotNull @Positive BigDecimal montantHt,
        @NotNull @PositiveOrZero BigDecimal tva,
        @NotBlank String devise,
        UUID categorieId,
        @NotBlank String statut,
        String notes
) {}

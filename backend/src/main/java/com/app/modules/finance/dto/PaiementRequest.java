package com.app.modules.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PaiementRequest(
        @NotNull @PastOrPresent LocalDate datePaiement,
        @NotNull @Positive BigDecimal montantTotal,
        @NotBlank String devise,
        String compte,
        @NotBlank String moyenPaiement,
        @NotEmpty List<PaiementLigneRequest> factures,
        String notes
) {}

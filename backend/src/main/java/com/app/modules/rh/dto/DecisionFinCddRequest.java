package com.app.modules.rh.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record DecisionFinCddRequest(
        @NotBlank String decision,           // RENOUVELLEMENT / CDI / NON_RENOUVELE
        LocalDate dateDecision,
        String commentaire
) {}


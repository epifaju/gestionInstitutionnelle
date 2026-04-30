package com.app.modules.rapports.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExportEtatPaieRequest(
        @NotNull @Min(2020) Integer annee,
        @NotNull @Min(1) @Max(12) Integer mois,
        String service
) {}


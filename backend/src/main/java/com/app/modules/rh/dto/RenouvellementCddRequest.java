package com.app.modules.rh.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RenouvellementCddRequest(
        @NotNull LocalDate nouvelleDateFin,
        String motif,
        String commentaire
) {}


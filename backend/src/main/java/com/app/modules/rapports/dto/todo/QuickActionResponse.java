package com.app.modules.rapports.dto.todo;

public record QuickActionResponse(
        boolean succes,
        String message,
        String nouveauStatut,
        long nouvelleCountSection
) {}


package com.app.modules.ged.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DocumentShareRequest(
        @NotNull UUID utilisateurId,
        boolean peutModifier,
        boolean peutSupprimer
) {}


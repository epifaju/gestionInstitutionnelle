package com.app.modules.rapports.dto.todo;

import jakarta.validation.constraints.NotBlank;

public record QuickActionRequest(
        @NotBlank String typeAction,
        String commentaire
) {}


package com.app.modules.rh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record TitreSejourRequest(
        @NotBlank String typeDocument,
        String numeroDocument, String paysEmetteur,
        LocalDate dateEmission,
        @NotNull LocalDate dateExpiration,
        String autoriteEmettrice
) {}


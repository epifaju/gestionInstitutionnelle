package com.app.modules.rh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record TraiterEcheanceRequest(
        @NotNull LocalDate dateTraitement,
        @NotBlank String commentaire
        // document uploadé séparément via multipart
) {}


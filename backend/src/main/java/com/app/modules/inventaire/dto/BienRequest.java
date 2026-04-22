package com.app.modules.inventaire.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BienRequest(
        @NotBlank String libelle,
        @NotBlank String categorie,
        @NotBlank String codeCategorie,
        LocalDate dateAcquisition,
        @NotNull @PositiveOrZero BigDecimal valeurAchat,
        String devise,
        String localisation,
        @NotBlank String etat,
        UUID responsableId,
        String description) {}

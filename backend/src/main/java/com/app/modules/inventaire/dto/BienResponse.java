package com.app.modules.inventaire.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record BienResponse(
        UUID id,
        String codeInventaire,
        String libelle,
        String categorie,
        String codeCategorie,
        LocalDate dateAcquisition,
        BigDecimal valeurAchat,
        String devise,
        String localisation,
        String etat,
        String responsableNomComplet,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}

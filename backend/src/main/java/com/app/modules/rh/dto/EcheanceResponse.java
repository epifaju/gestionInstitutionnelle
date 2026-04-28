package com.app.modules.rh.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record EcheanceResponse(
        UUID id, UUID salarieId, String salarieNomComplet, String service,
        String matricule, String typeEcheance, String titre, String description,
        LocalDate dateEcheance, String statut, Integer priorite,
        String responsableNomComplet,
        LocalDate dateTraitement, String commentaireTraitement,
        String traitePar, String documentPreuveUrl,
        Integer joursRestants,              // calculé : négatif si dépassé
        String niveauUrgence,               // CRITIQUE/URGENT/ATTENTION/NORMAL
        LocalDateTime createdAt
) {}


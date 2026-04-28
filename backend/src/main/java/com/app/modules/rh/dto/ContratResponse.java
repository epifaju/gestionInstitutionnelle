package com.app.modules.rh.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContratResponse(
        UUID id, UUID salarieId, String salarieNomComplet, String matricule, String service,
        String typeContrat, LocalDate dateDebutContrat, LocalDate dateFinContrat,
        LocalDate dateFinPeriodeEssai, Integer dureeEssaiMois,
        String numeroContrat, String intitulePoste, String motifCdd,
        String conventionCollective, Integer renouvellementNumero,
        UUID contratParentId, String decisionFin, LocalDate dateDecision,
        String commentaireDecision, String contratSigneUrl, boolean actif,
        Integer joursAvantFin,              // calculé : null si CDI ou passé
        String niveauUrgence,               // CRITIQUE/URGENT/ATTENTION/NORMAL
        LocalDateTime createdAt
) {}


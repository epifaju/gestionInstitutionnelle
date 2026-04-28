package com.app.modules.rh.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record VisiteMedicaleResponse(
        UUID id, UUID salarieId, String salarieNomComplet,
        String typeVisite, LocalDate datePlanifiee, LocalDate dateRealisee,
        String medecin, String centreMedical, String statut,
        String resultat, String restrictions,
        LocalDate prochaineVisite, Integer periodiciteMois,
        String compteRenduUrl, LocalDateTime createdAt
) {}


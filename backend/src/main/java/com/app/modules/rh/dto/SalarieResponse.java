package com.app.modules.rh.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SalarieResponse(
        UUID id,
        String matricule,
        String nom,
        String prenom,
        String email,
        String telephone,
        String poste,
        String service,
        LocalDate dateEmbauche,
        String typeContrat,
        String statut,
        String nationalite,
        String adresse,
        SalaireActuel salaireActuel,
        DroitsCongesDto droitsConges,
        LocalDateTime createdAt
) {
}

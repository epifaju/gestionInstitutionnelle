package com.app.modules.rh.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CongeResponse(
        UUID id,
        UUID salarieId,
        String salarieNomComplet,
        String service,
        String typeConge,
        LocalDate dateDebut,
        LocalDate dateFin,
        BigDecimal nbJours,
        String statut,
        String valideurNomComplet,
        LocalDateTime dateValidation,
        String motifRejet,
        String commentaire,
        LocalDateTime createdAt
) {
}

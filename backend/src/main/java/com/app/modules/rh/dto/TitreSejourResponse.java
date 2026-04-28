package com.app.modules.rh.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TitreSejourResponse(
        UUID id, UUID salarieId, String salarieNomComplet,
        String typeDocument, String numeroDocument, String paysEmetteur,
        LocalDate dateEmission, LocalDate dateExpiration,
        String autoriteEmettrice, String documentUrl,
        String statutRenouvellement,
        Integer joursAvantExpiration,       // calculé
        String niveauUrgence,
        LocalDateTime createdAt
) {}


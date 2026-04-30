package com.app.modules.rapports.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ExportJobResponse(
        UUID id,
        String typeExport,
        String statut,
        Integer progression,
        String fichierUrl,
        String nomFichier,
        Long tailleOctets,
        Integer nbLignes,
        String messageErreur,
        LocalDateTime expireA,
        LocalDateTime createdAt
) {}


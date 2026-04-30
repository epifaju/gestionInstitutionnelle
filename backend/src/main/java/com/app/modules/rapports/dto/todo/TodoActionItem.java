package com.app.modules.rapports.dto.todo;

import java.time.LocalDateTime;
import java.util.UUID;

public record TodoActionItem(
        UUID id,
        String type,
        String titre,
        String sousTitre,
        String statut,
        String niveauUrgence, // CRITIQUE | URGENT | NORMAL
        String lienAction,
        LocalDateTime dateCreation,
        LocalDateTime dateEcheance,
        String metadonnees
) {}


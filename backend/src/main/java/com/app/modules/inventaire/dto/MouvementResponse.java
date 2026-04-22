package com.app.modules.inventaire.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MouvementResponse(
        UUID id,
        String typeMouvement,
        String champModifie,
        String ancienneValeur,
        String nouvelleValeur,
        String motif,
        String auteurNomComplet,
        LocalDateTime dateMouvement) {}

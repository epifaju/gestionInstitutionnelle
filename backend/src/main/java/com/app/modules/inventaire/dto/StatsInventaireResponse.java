package com.app.modules.inventaire.dto;

import java.math.BigDecimal;
import java.util.List;

public record StatsInventaireResponse(
        BigDecimal valeurTotaleParc,
        List<CompteParEtat> repartitionEtat,
        List<CompteParCategorie> repartitionCategorie) {

    public record CompteParEtat(String etat, long count) {}

    public record CompteParCategorie(String categorie, long count) {}
}

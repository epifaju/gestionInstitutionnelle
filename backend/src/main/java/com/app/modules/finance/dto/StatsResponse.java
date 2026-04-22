package com.app.modules.finance.dto;

import java.math.BigDecimal;
import java.util.List;

public record StatsResponse(
        int annee,
        int mois,
        BigDecimal totalDepenses,
        BigDecimal totalRecettes,
        BigDecimal solde,
        String devise,
        long nbFactures,
        long nbFacturesEnAttente,
        List<CategorieMontantDto> depensesParCategorie,
        List<CategorieMontantDto> recettesParCategorie
) {}

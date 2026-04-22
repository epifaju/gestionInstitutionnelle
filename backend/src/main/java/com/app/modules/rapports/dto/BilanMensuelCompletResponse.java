package com.app.modules.rapports.dto;

import com.app.modules.finance.dto.CategorieMontantDto;
import com.app.modules.finance.dto.StatsResponse;

import java.math.BigDecimal;
import java.util.List;

/** Bilan mensuel : stats financières + agrégats RH. */
public record BilanMensuelCompletResponse(
        int annee,
        int mois,
        BigDecimal totalDepenses,
        BigDecimal totalRecettes,
        BigDecimal solde,
        String devise,
        long nbFactures,
        long nbFacturesEnAttente,
        List<CategorieMontantDto> depensesParCategorie,
        List<CategorieMontantDto> recettesParCategorie,
        long effectifsActifs,
        long nbCongesDuMois) {

    public static BilanMensuelCompletResponse from(StatsResponse stats, long effectifsActifs, long nbCongesDuMois) {
        return new BilanMensuelCompletResponse(
                stats.annee(),
                stats.mois(),
                stats.totalDepenses(),
                stats.totalRecettes(),
                stats.solde(),
                stats.devise(),
                stats.nbFactures(),
                stats.nbFacturesEnAttente(),
                stats.depensesParCategorie(),
                stats.recettesParCategorie(),
                effectifsActifs,
                nbCongesDuMois);
    }
}

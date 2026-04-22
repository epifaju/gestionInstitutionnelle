package com.app.modules.rapports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        MoisCourant moisCourant,
        DashboardKpis kpis,
        List<EvolutionMois> evolution6Mois,
        List<AlerteBudgetItem> alertesBudget,
        List<TopFournisseurItem> top5Fournisseurs,
        List<CongeEnCoursItem> congesEnCours) {

    public record MoisCourant(int annee, int mois) {}

    public record DashboardKpis(
            BigDecimal totalDepenses,
            BigDecimal totalRecettes,
            BigDecimal solde,
            long effectifsActifs,
            long congesEnCours,
            BigDecimal valeurParcMateriel) {}

    public record EvolutionMois(String mois, BigDecimal depenses, BigDecimal recettes) {}

    public record AlerteBudgetItem(String categorie, BigDecimal tauxExecution, boolean alerte) {}

    public record TopFournisseurItem(String fournisseur, BigDecimal montant) {}

    public record CongeEnCoursItem(String salarieNomComplet, LocalDate dateDebut, LocalDate dateFin) {}
}

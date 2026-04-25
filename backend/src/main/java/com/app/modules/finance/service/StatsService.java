package com.app.modules.finance.service;

import com.app.modules.finance.dto.CategorieMontantDto;
import com.app.modules.finance.dto.DeviseRepartitionDto;
import com.app.modules.finance.dto.StatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final JdbcTemplate jdbcTemplate;
    private final ExchangeRateService exchangeRateService;

    @Transactional(readOnly = true)
    public StatsResponse getStatsMensuelles(UUID orgId, int annee, int mois) {
        BigDecimal totalDepenses =
                jdbcTemplate.queryForObject(
                        """
                                SELECT COALESCE(SUM(montant_ttc * taux_change_eur), 0)
                                FROM factures
                                WHERE organisation_id = ?
                                  AND EXTRACT(YEAR FROM date_facture) = ?
                                  AND EXTRACT(MONTH FROM date_facture) = ?
                                  AND statut IN ('A_PAYER', 'PAYE')
                                """,
                        BigDecimal.class,
                        orgId,
                        annee,
                        mois);

        BigDecimal totalRecettes =
                jdbcTemplate.queryForObject(
                        """
                                SELECT COALESCE(SUM(montant * taux_change_eur), 0)
                                FROM recettes
                                WHERE organisation_id = ?
                                  AND EXTRACT(YEAR FROM date_recette) = ?
                                  AND EXTRACT(MONTH FROM date_recette) = ?
                                """,
                        BigDecimal.class,
                        orgId,
                        annee,
                        mois);

        Long nbFactures =
                jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*) FROM factures
                                WHERE organisation_id = ?
                                  AND EXTRACT(YEAR FROM date_facture) = ?
                                  AND EXTRACT(MONTH FROM date_facture) = ?
                                """,
                        Long.class,
                        orgId,
                        annee,
                        mois);

        Long nbFacturesEnAttente =
                jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*) FROM factures
                                WHERE organisation_id = ?
                                  AND EXTRACT(YEAR FROM date_facture) = ?
                                  AND EXTRACT(MONTH FROM date_facture) = ?
                                  AND statut = 'A_PAYER'
                                """,
                        Long.class,
                        orgId,
                        annee,
                        mois);

        List<CategorieMontantDto> depensesParCategorie =
                jdbcTemplate.query(
                        """
                                SELECT COALESCE(cd.libelle, 'Sans catégorie') AS lib,
                                       COALESCE(SUM(f.montant_ttc * f.taux_change_eur), 0) AS m
                                FROM factures f
                                LEFT JOIN categories_depenses cd ON cd.id = f.categorie_id
                                WHERE f.organisation_id = ?
                                  AND EXTRACT(YEAR FROM f.date_facture) = ?
                                  AND EXTRACT(MONTH FROM f.date_facture) = ?
                                  AND f.statut IN ('A_PAYER', 'PAYE')
                                GROUP BY COALESCE(cd.libelle, 'Sans catégorie')
                                ORDER BY lib
                                """,
                        (rs, rowNum) -> new CategorieMontantDto(rs.getString("lib"), rs.getBigDecimal("m")),
                        orgId,
                        annee,
                        mois);

        List<CategorieMontantDto> recettesParCategorie =
                jdbcTemplate.query(
                        """
                                SELECT COALESCE(cd.libelle, 'Sans catégorie') AS lib,
                                       COALESCE(SUM(r.montant * r.taux_change_eur), 0) AS m
                                FROM recettes r
                                LEFT JOIN categories_depenses cd ON cd.id = r.categorie_id
                                WHERE r.organisation_id = ?
                                  AND EXTRACT(YEAR FROM r.date_recette) = ?
                                  AND EXTRACT(MONTH FROM r.date_recette) = ?
                                GROUP BY COALESCE(cd.libelle, 'Sans catégorie')
                                ORDER BY lib
                                """,
                        (rs, rowNum) -> new CategorieMontantDto(rs.getString("lib"), rs.getBigDecimal("m")),
                        orgId,
                        annee,
                        mois);

        BigDecimal solde = totalRecettes.subtract(totalDepenses);

        // FX enrichment: gain/loss vs today's rate + currency repartition
        LocalDate start = LocalDate.of(annee, mois, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<DeviseRepartitionDto> repartition =
                jdbcTemplate.query(
                        """
                                SELECT devise,
                                       COALESCE(SUM(montant_original), 0) AS montant_original,
                                       COALESCE(SUM(montant_eur), 0) AS montant_eur
                                FROM (
                                    SELECT f.devise AS devise,
                                           SUM(f.montant_ttc) AS montant_original,
                                           SUM(f.montant_ttc * f.taux_change_eur) AS montant_eur
                                    FROM factures f
                                    WHERE f.organisation_id = ?
                                      AND f.devise <> 'EUR'
                                      AND f.date_facture BETWEEN ? AND ?
                                      AND f.statut IN ('A_PAYER', 'PAYE')
                                    GROUP BY f.devise
                                    UNION ALL
                                    SELECT r.devise AS devise,
                                           SUM(r.montant) AS montant_original,
                                           SUM(r.montant * r.taux_change_eur) AS montant_eur
                                    FROM recettes r
                                    WHERE r.organisation_id = ?
                                      AND r.devise <> 'EUR'
                                      AND r.date_recette BETWEEN ? AND ?
                                    GROUP BY r.devise
                                ) x
                                GROUP BY devise
                                ORDER BY devise
                                """,
                        (rs, rowNum) ->
                                new DeviseRepartitionDto(
                                        rs.getString("devise"),
                                        rs.getBigDecimal("montant_original"),
                                        rs.getBigDecimal("montant_eur")),
                        orgId,
                        start,
                        end,
                        orgId,
                        start,
                        end);

        BigDecimal gainPerteChange = BigDecimal.ZERO;
        for (DeviseRepartitionDto d : repartition) {
            String devise = d.devise() == null ? "EUR" : d.devise().trim().toUpperCase(Locale.ROOT);
            if ("EUR".equals(devise)) continue;
            BigDecimal tauxActuel = exchangeRateService.getTauxDuJour(devise, "EUR");
            BigDecimal eurAuTauxActuel =
                    d.montantOriginal().multiply(tauxActuel).setScale(2, RoundingMode.HALF_UP);
            // applied (stored) minus current
            gainPerteChange = gainPerteChange.add(d.montantEur().subtract(eurAuTauxActuel));
        }
        gainPerteChange = gainPerteChange.setScale(2, RoundingMode.HALF_UP);

        return new StatsResponse(
                annee,
                mois,
                totalDepenses,
                totalRecettes,
                solde,
                "EUR",
                nbFactures != null ? nbFactures : 0L,
                nbFacturesEnAttente != null ? nbFacturesEnAttente : 0L,
                gainPerteChange,
                repartition,
                depensesParCategorie,
                recettesParCategorie);
    }
}

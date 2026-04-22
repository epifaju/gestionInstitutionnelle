package com.app.modules.finance.service;

import com.app.modules.finance.dto.CategorieMontantDto;
import com.app.modules.finance.dto.StatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final JdbcTemplate jdbcTemplate;

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
        return new StatsResponse(
                annee,
                mois,
                totalDepenses,
                totalRecettes,
                solde,
                "EUR",
                nbFactures != null ? nbFactures : 0L,
                nbFacturesEnAttente != null ? nbFacturesEnAttente : 0L,
                depensesParCategorie,
                recettesParCategorie);
    }
}

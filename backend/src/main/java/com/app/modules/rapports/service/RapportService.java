package com.app.modules.rapports.service;

import com.app.modules.auth.entity.Role;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.finance.dto.CategorieMontantDto;
import com.app.modules.finance.dto.StatsResponse;
import com.app.modules.finance.service.StatsService;
import com.app.modules.inventaire.service.InventaireService;
import com.app.modules.rapports.dto.BilanMensuelCompletResponse;
import com.app.modules.rapports.dto.DashboardResponse;
import com.app.shared.exception.BusinessException;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RapportService {

    private static final String UTF8_BOM = "\uFEFF";

    private final JdbcTemplate jdbcTemplate;
    private final StatsService statsService;
    private final OrganisationRepository organisationRepository;
    private final InventaireService inventaireService;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID orgId, UUID userId, Role role) {
        LocalDate today = LocalDate.now();
        int annee = today.getYear();
        int mois = today.getMonthValue();
        boolean employe = role == Role.EMPLOYE;

        int seuil =
                organisationRepository
                        .findById(orgId)
                        .map(o -> o.getAlerteBudgetPct() != null ? o.getAlerteBudgetPct() : 80)
                        .orElse(80);

        BigDecimal totalDepenses = BigDecimal.ZERO;
        BigDecimal totalRecettes = BigDecimal.ZERO;
        BigDecimal solde = BigDecimal.ZERO;
        List<DashboardResponse.EvolutionMois> evolution = new ArrayList<>();
        List<DashboardResponse.AlerteBudgetItem> alertes = new ArrayList<>();
        List<DashboardResponse.TopFournisseurItem> top5 = new ArrayList<>();

        if (!employe) {
            StatsResponse cur = statsService.getStatsMensuelles(orgId, annee, mois);
            totalDepenses = cur.totalDepenses();
            totalRecettes = cur.totalRecettes();
            solde = cur.solde();

            YearMonth ym = YearMonth.from(today);
            for (int i = 5; i >= 0; i--) {
                YearMonth mm = ym.minusMonths(i);
                StatsResponse st = statsService.getStatsMensuelles(orgId, mm.getYear(), mm.getMonthValue());
                evolution.add(
                        new DashboardResponse.EvolutionMois(
                                mm.toString(), st.totalDepenses(), st.totalRecettes()));
            }

            alertes.addAll(loadAlertesBudget(orgId, annee, seuil));
            top5.addAll(loadTop5Fournisseurs(orgId, annee));
        } else {
            YearMonth ym = YearMonth.from(today);
            for (int i = 5; i >= 0; i--) {
                YearMonth mm = ym.minusMonths(i);
                evolution.add(
                        new DashboardResponse.EvolutionMois(
                                mm.toString(), BigDecimal.ZERO, BigDecimal.ZERO));
            }
        }

        /* EMPLOYE : pas d’agrégats finance/RH/inventaire (matrice PRD §9). */
        long effectifsActifs = employe ? 0L : countEffectifsActifs(orgId);
        long congesEncoursCount = countCongesEnCours(orgId, employe ? userId : null);
        BigDecimal valeurParc =
                employe ? BigDecimal.ZERO : inventaireService.getStats(orgId).valeurTotaleParc();

        List<DashboardResponse.CongeEnCoursItem> congesListe = loadCongesEnCours(orgId, employe ? userId : null);

        DashboardResponse.DashboardKpis kpis =
                new DashboardResponse.DashboardKpis(
                        employe ? BigDecimal.ZERO : totalDepenses,
                        employe ? BigDecimal.ZERO : totalRecettes,
                        employe ? BigDecimal.ZERO : solde,
                        effectifsActifs,
                        congesEncoursCount,
                        valeurParc);

        return new DashboardResponse(
                new DashboardResponse.MoisCourant(annee, mois),
                kpis,
                evolution,
                employe ? List.of() : alertes,
                employe ? List.of() : top5,
                congesListe);
    }

    private List<DashboardResponse.AlerteBudgetItem> loadAlertesBudget(UUID orgId, int annee, int seuil) {
        return jdbcTemplate.query(
                """
                        SELECT v.categorie_libelle, v.taux_execution_pct
                        FROM v_execution_budget v
                        INNER JOIN budgets_annuels b ON b.id = v.budget_id
                        WHERE b.organisation_id = ?
                          AND b.annee = ?
                          AND b.statut = 'VALIDE'
                          AND v.taux_execution_pct >= ?
                        ORDER BY v.taux_execution_pct DESC
                        """,
                (rs, row) -> {
                    BigDecimal taux = rs.getBigDecimal("taux_execution_pct");
                    return new DashboardResponse.AlerteBudgetItem(
                            rs.getString("categorie_libelle"),
                            taux,
                            true);
                },
                orgId,
                annee,
                BigDecimal.valueOf(seuil));
    }

    private List<DashboardResponse.TopFournisseurItem> loadTop5Fournisseurs(UUID orgId, int annee) {
        return jdbcTemplate.query(
                """
                        SELECT f.fournisseur, COALESCE(SUM(f.montant_ttc * f.taux_change_eur), 0) AS m
                        FROM factures f
                        WHERE f.organisation_id = ?
                          AND EXTRACT(YEAR FROM f.date_facture) = ?
                          AND f.statut IN ('A_PAYER', 'PAYE')
                        GROUP BY f.fournisseur
                        ORDER BY m DESC
                        LIMIT 5
                        """,
                (rs, row) ->
                        new DashboardResponse.TopFournisseurItem(rs.getString("fournisseur"), rs.getBigDecimal("m")),
                orgId,
                annee);
    }

    private long countEffectifsActifs(UUID orgId) {
        Long n =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM salaries WHERE organisation_id = ? AND statut = 'ACTIF'",
                        Long.class,
                        orgId);
        return n != null ? n : 0L;
    }

    private long countCongesEnCours(UUID orgId, UUID utilisateurIdFiltre) {
        if (utilisateurIdFiltre != null) {
            Long n =
                    jdbcTemplate.queryForObject(
                            """
                                    SELECT COUNT(*) FROM conges_absences c
                                    INNER JOIN salaries s ON s.id = c.salarie_id
                                    WHERE c.organisation_id = ?
                                      AND c.statut = 'VALIDE'
                                      AND c.date_debut <= CURRENT_DATE AND c.date_fin >= CURRENT_DATE
                                      AND s.utilisateur_id = ?
                                    """,
                            Long.class,
                            orgId,
                            utilisateurIdFiltre);
            return n != null ? n : 0L;
        }
        Long n =
                jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*) FROM conges_absences c
                                WHERE c.organisation_id = ?
                                  AND c.statut = 'VALIDE'
                                  AND c.date_debut <= CURRENT_DATE AND c.date_fin >= CURRENT_DATE
                                """,
                        Long.class,
                        orgId);
        return n != null ? n : 0L;
    }

    private List<DashboardResponse.CongeEnCoursItem> loadCongesEnCours(UUID orgId, UUID utilisateurIdFiltre) {
        if (utilisateurIdFiltre != null) {
            return jdbcTemplate.query(
                    """
                            SELECT s.prenom || ' ' || s.nom AS nom, c.date_debut, c.date_fin
                            FROM conges_absences c
                            INNER JOIN salaries s ON s.id = c.salarie_id
                            WHERE c.organisation_id = ?
                              AND c.statut = 'VALIDE'
                              AND c.date_debut <= CURRENT_DATE AND c.date_fin >= CURRENT_DATE
                              AND s.utilisateur_id = ?
                            ORDER BY c.date_debut
                            """,
                    (rs, row) ->
                            new DashboardResponse.CongeEnCoursItem(
                                    rs.getString("nom"),
                                    rs.getDate("date_debut").toLocalDate(),
                                    rs.getDate("date_fin").toLocalDate()),
                    orgId,
                    utilisateurIdFiltre);
        }
        return jdbcTemplate.query(
                """
                        SELECT s.prenom || ' ' || s.nom AS nom, c.date_debut, c.date_fin
                        FROM conges_absences c
                        INNER JOIN salaries s ON s.id = c.salarie_id
                        WHERE c.organisation_id = ?
                          AND c.statut = 'VALIDE'
                          AND c.date_debut <= CURRENT_DATE AND c.date_fin >= CURRENT_DATE
                        ORDER BY c.date_debut
                        """,
                (rs, row) ->
                        new DashboardResponse.CongeEnCoursItem(
                                rs.getString("nom"),
                                rs.getDate("date_debut").toLocalDate(),
                                rs.getDate("date_fin").toLocalDate()),
                orgId);
    }

    @Transactional(readOnly = true)
    public BilanMensuelCompletResponse getBilanMensuel(UUID orgId, int annee, int mois) {
        StatsResponse stats = statsService.getStatsMensuelles(orgId, annee, mois);
        long effectifs = countEffectifsActifs(orgId);
        long nbConges = countCongesDuMois(orgId, annee, mois);
        return BilanMensuelCompletResponse.from(stats, effectifs, nbConges);
    }

    private long countCongesDuMois(UUID orgId, int annee, int mois) {
        LocalDate debut = LocalDate.of(annee, mois, 1);
        LocalDate fin = YearMonth.of(annee, mois).atEndOfMonth();
        Long n =
                jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*) FROM conges_absences c
                                WHERE c.organisation_id = ?
                                  AND c.statut = 'VALIDE'
                                  AND c.date_debut <= ? AND c.date_fin >= ?
                                """,
                        Long.class,
                        orgId,
                        fin,
                        debut);
        return n != null ? n : 0L;
    }

    @Transactional(readOnly = true)
    public byte[] exportBilanMensuelPdf(UUID orgId, int annee, int mois, String organisationNom) {
        StatsResponse stats = statsService.getStatsMensuelles(orgId, annee, mois);
        long effectifs = countEffectifsActifs(orgId);
        long nbConges = countCongesDuMois(orgId, annee, mois);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);

        doc.add(new Paragraph("Bilan mensuel").setBold().setFontSize(18).setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph(organisationNom != null ? organisationNom : "").setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph(String.format("%02d/%d", mois, annee)).setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph("\n"));

        doc.add(new Paragraph("Dépenses par catégorie").setBold());
        doc.add(buildCategorieTable(stats.depensesParCategorie()));
        doc.add(new Paragraph("\n"));

        doc.add(new Paragraph("Recettes par catégorie").setBold());
        doc.add(buildCategorieTable(stats.recettesParCategorie()));
        doc.add(new Paragraph("\n"));

        doc.add(
                new Paragraph(
                        "Solde net : "
                                + stats.solde().toPlainString()
                                + " "
                                + stats.devise()));

        doc.add(new Paragraph("\nIndicateurs RH").setBold());
        doc.add(new Paragraph("Effectifs actifs : " + effectifs));
        doc.add(new Paragraph("Nombre de congés sur la période : " + nbConges));

        doc.close();
        return baos.toByteArray();
    }

    private Table buildCategorieTable(List<CategorieMontantDto> rows) {
        Table table = new Table(UnitValue.createPercentArray(new float[] {70f, 30f})).useAllAvailableWidth();
        table.addHeaderCell("Catégorie");
        table.addHeaderCell("Montant (EUR)");
        for (CategorieMontantDto r : rows) {
            table.addCell(r.categorie() != null ? r.categorie() : "");
            table.addCell(r.montant() != null ? r.montant().toPlainString() : "0");
        }
        return table;
    }

    @Transactional(readOnly = true)
    public byte[] exportBilanAnnuelExcel(UUID orgId, int annee) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet resume = wb.createSheet("Résumé");
            Row h = resume.createRow(0);
            h.createCell(0).setCellValue("Mois");
            h.createCell(1).setCellValue("Dépenses");
            h.createCell(2).setCellValue("Recettes");
            h.createCell(3).setCellValue("Solde");
            for (int m = 1; m <= 12; m++) {
                StatsResponse st = statsService.getStatsMensuelles(orgId, annee, m);
                Row row = resume.createRow(m);
                row.createCell(0).setCellValue(m);
                row.createCell(1).setCellValue(st.totalDepenses().doubleValue());
                row.createCell(2).setCellValue(st.totalRecettes().doubleValue());
                row.createCell(3).setCellValue(st.solde().doubleValue());
            }

            Sheet dep = wb.createSheet("Dépenses");
            List<String[]> factures = loadFacturesAnnee(orgId, annee);
            for (int i = 0; i < factures.size(); i++) {
                Row row = dep.createRow(i);
                String[] cells = factures.get(i);
                for (int c = 0; c < cells.length; c++) {
                    row.createCell(c).setCellValue(cells[c] != null ? cells[c] : "");
                }
            }

            Sheet rec = wb.createSheet("Recettes");
            List<String[]> recettes = loadRecettesAnnee(orgId, annee);
            for (int i = 0; i < recettes.size(); i++) {
                Row row = rec.createRow(i);
                String[] cells = recettes.get(i);
                for (int c = 0; c < cells.length; c++) {
                    row.createCell(c).setCellValue(cells[c] != null ? cells[c] : "");
                }
            }

            Sheet bud = wb.createSheet("Budget");
            List<String[]> budgetRows = loadBudgetPrevuRealise(orgId, annee);
            for (int i = 0; i < budgetRows.size(); i++) {
                Row row = bud.createRow(i);
                String[] cells = budgetRows.get(i);
                for (int c = 0; c < cells.length; c++) {
                    row.createCell(c).setCellValue(cells[c] != null ? cells[c] : "");
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Erreur génération Excel", e);
        }
    }

    private List<String[]> loadFacturesAnnee(UUID orgId, int annee) {
        List<String[]> out = new ArrayList<>();
        out.add(
                new String[] {
                    "Référence",
                    "Fournisseur",
                    "Date",
                    "Montant TTC EUR",
                    "Statut",
                    "Catégorie"
                });
        jdbcTemplate.query(
                """
                        SELECT f.reference, f.fournisseur, f.date_facture::text,
                               (f.montant_ttc * f.taux_change_eur)::text,
                               f.statut::text, COALESCE(cd.libelle, '')
                        FROM factures f
                        LEFT JOIN categories_depenses cd ON cd.id = f.categorie_id
                        WHERE f.organisation_id = ? AND EXTRACT(YEAR FROM f.date_facture) = ?
                        ORDER BY f.date_facture
                        """,
                (ResultSetExtractor<Void>)
                        rs -> {
                            while (rs.next()) {
                                out.add(
                                        new String[] {
                                            rs.getString(1),
                                            rs.getString(2),
                                            rs.getString(3),
                                            rs.getString(4),
                                            rs.getString(5),
                                            rs.getString(6)
                                        });
                            }
                            return null;
                        },
                orgId,
                annee);
        return out;
    }

    private List<String[]> loadRecettesAnnee(UUID orgId, int annee) {
        List<String[]> out = new ArrayList<>();
        out.add(
                new String[] {
                    "Date", "Montant EUR", "Type", "Description", "Catégorie"
                });
        jdbcTemplate.query(
                """
                        SELECT r.date_recette::text,
                               (r.montant * r.taux_change_eur)::text,
                               r.type_recette::text,
                               COALESCE(r.description, ''),
                               COALESCE(cd.libelle, '')
                        FROM recettes r
                        LEFT JOIN categories_depenses cd ON cd.id = r.categorie_id
                        WHERE r.organisation_id = ? AND EXTRACT(YEAR FROM r.date_recette) = ?
                        ORDER BY r.date_recette
                        """,
                (ResultSetExtractor<Void>)
                        rs -> {
                            while (rs.next()) {
                                out.add(
                                        new String[] {
                                            rs.getString(1),
                                            rs.getString(2),
                                            rs.getString(3),
                                            rs.getString(4),
                                            rs.getString(5)
                                        });
                            }
                            return null;
                        },
                orgId,
                annee);
        return out;
    }

    private List<String[]> loadBudgetPrevuRealise(UUID orgId, int annee) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[] {"Catégorie", "Type", "Prévu EUR", "Réalisé EUR", "Écart", "% exécution"});
        jdbcTemplate.query(
                """
                        SELECT v.categorie_libelle, v.type::text, v.montant_prevu::text,
                               v.montant_realise::text, v.ecart::text, v.taux_execution_pct::text
                        FROM v_execution_budget v
                        INNER JOIN budgets_annuels b ON b.id = v.budget_id
                        WHERE b.organisation_id = ? AND b.annee = ? AND b.statut = 'VALIDE'
                        ORDER BY v.categorie_libelle, v.type
                        """,
                (ResultSetExtractor<Void>)
                        rs -> {
                            while (rs.next()) {
                                out.add(
                                        new String[] {
                                            rs.getString(1),
                                            rs.getString(2),
                                            rs.getString(3),
                                            rs.getString(4),
                                            rs.getString(5),
                                            rs.getString(6)
                                        });
                            }
                            return null;
                        },
                orgId,
                annee);
        return out;
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(UUID orgId, String entite) {
        return switch (entite) {
            case "factures" -> csvFactures(orgId);
            case "recettes" -> csvRecettes(orgId);
            case "salaires" -> csvSalaires(orgId);
            default -> throw BusinessException.badRequest("EXPORT_ENTITE_INVALIDE");
        };
    }

    private byte[] csvFactures(UUID orgId) {
        StringBuilder sb = new StringBuilder();
        sb.append("reference;fournisseur;date_facture;montant_ttc_eur;statut;categorie\n");
        jdbcTemplate.query(
                """
                        SELECT f.reference, f.fournisseur, f.date_facture::text,
                               (f.montant_ttc * f.taux_change_eur)::text,
                               f.statut::text, COALESCE(cd.libelle, '')
                        FROM factures f
                        LEFT JOIN categories_depenses cd ON cd.id = f.categorie_id
                        WHERE f.organisation_id = ?
                        ORDER BY f.date_facture DESC
                        """,
                (ResultSetExtractor<Void>)
                        rs -> {
                            while (rs.next()) {
                                sb.append(escapeCsv(rs.getString(1)))
                                        .append(';')
                                        .append(escapeCsv(rs.getString(2)))
                                        .append(';')
                                        .append(escapeCsv(rs.getString(3)))
                                        .append(';')
                                        .append(escapeCsv(rs.getString(4)))
                                        .append(';')
                                        .append(escapeCsv(rs.getString(5)))
                                        .append(';')
                                        .append(escapeCsv(rs.getString(6)))
                                        .append('\n');
                            }
                            return null;
                        },
                orgId);
        return (UTF8_BOM + sb).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] csvRecettes(UUID orgId) {
        StringBuilder sb = new StringBuilder();
        sb.append("date_recette;montant_eur;type_recette;description;categorie\n");
        jdbcTemplate.query(
                """
                        SELECT r.date_recette::text, (r.montant * r.taux_change_eur)::text,
                               r.type_recette::text, COALESCE(r.description, ''), COALESCE(cd.libelle, '')
                        FROM recettes r
                        LEFT JOIN categories_depenses cd ON cd.id = r.categorie_id
                        WHERE r.organisation_id = ?
                        ORDER BY r.date_recette DESC
                        """,
                (ResultSetExtractor<Void>)
                        rs -> {
                            while (rs.next()) {
                                for (int i = 1; i <= 5; i++) {
                                    if (i > 1) {
                                        sb.append(';');
                                    }
                                    sb.append(escapeCsv(rs.getString(i)));
                                }
                                sb.append('\n');
                            }
                            return null;
                        },
                orgId);
        return (UTF8_BOM + sb).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] csvSalaires(UUID orgId) {
        StringBuilder sb = new StringBuilder();
        sb.append("salarie;mois;annee;montant_eur;devise;statut;date_paiement\n");
        jdbcTemplate.query(
                """
                        SELECT s.prenom || ' ' || s.nom, p.mois, p.annee,
                               p.montant::text, p.devise, p.statut::text,
                               COALESCE(p.date_paiement::text, '')
                        FROM paiements_salaires p
                        INNER JOIN salaries s ON s.id = p.salarie_id
                        WHERE p.organisation_id = ?
                        ORDER BY p.annee DESC, p.mois DESC, s.nom
                        """,
                (ResultSetExtractor<Void>)
                        rs -> {
                            while (rs.next()) {
                                sb.append(escapeCsv(rs.getString(1)))
                                        .append(';')
                                        .append(rs.getInt(2))
                                        .append(';')
                                        .append(rs.getInt(3))
                                        .append(';')
                                        .append(escapeCsv(rs.getString(4)))
                                        .append(';')
                                        .append(escapeCsv(rs.getString(5)))
                                        .append(';')
                                        .append(escapeCsv(rs.getString(6)))
                                        .append(';')
                                        .append(escapeCsv(rs.getString(7)))
                                        .append('\n');
                            }
                            return null;
                        },
                orgId);
        return (UTF8_BOM + sb).getBytes(StandardCharsets.UTF_8);
    }

    private static String escapeCsv(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\"", "\"\"");
        if (t.contains(";") || t.contains("\n") || t.contains("\"")) {
            return "\"" + t + "\"";
        }
        return t;
    }
}

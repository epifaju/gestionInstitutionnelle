package com.app.modules.budget.service;

import com.app.audit.AuditLogService;
import com.app.modules.auth.entity.Organisation;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.budget.dto.BudgetRequest;
import com.app.modules.budget.dto.BudgetResponse;
import com.app.modules.budget.dto.LigneBudgetRequest;
import com.app.modules.budget.dto.LigneBudgetResponse;
import com.app.modules.budget.dto.ModifierLigneBudgetRequest;
import com.app.modules.budget.entity.BudgetAnnuel;
import com.app.modules.budget.entity.LigneBudget;
import com.app.modules.budget.entity.StatutBudget;
import com.app.modules.budget.repository.BudgetAnnuelRepository;
import com.app.modules.budget.repository.LigneBudgetRepository;
import com.app.modules.finance.entity.CategorieDepense;
import com.app.modules.finance.entity.TypeCategorie;
import com.app.modules.finance.repository.CategorieDepenseRepository;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetAnnuelRepository budgetAnnuelRepository;
    private final LigneBudgetRepository ligneBudgetRepository;
    private final CategorieDepenseRepository categorieDepenseRepository;
    private final OrganisationRepository organisationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public BudgetResponse getBudget(UUID orgId, int annee) {
        BudgetAnnuel b =
                budgetAnnuelRepository
                        .findByOrganisationIdAndAnneeAndStatut(orgId, annee, StatutBudget.VALIDE)
                        .or(() -> budgetAnnuelRepository.findByOrganisationIdAndAnneeAndStatut(orgId, annee, StatutBudget.BROUILLON))
                        .orElseThrow(() -> BusinessException.notFound("BUDGET_ABSENT"));
        return toResponse(b, orgId);
    }

    @Transactional
    public BudgetResponse creer(BudgetRequest req, UUID orgId, UUID userId) {
        /*
         * PRD v3 (§8.3 Révision):
         * - On peut créer un nouveau budget BROUILLON même si un budget VALIDE existe déjà pour l'année.
         * - On doit toutefois empêcher plusieurs BROUILLONS simultanés pour une même année.
         */
        if (budgetAnnuelRepository.existsByOrganisationIdAndAnneeAndStatutIn(
                orgId, req.annee(), EnumSet.of(StatutBudget.BROUILLON))) {
            throw BusinessException.badRequest("BUDGET_BROUILLON_EXISTE");
        }
        BudgetAnnuel b = new BudgetAnnuel();
        b.setOrganisationId(orgId);
        b.setAnnee(req.annee());
        b.setStatut(StatutBudget.BROUILLON);
        b.setNotes(req.notes());
        b = budgetAnnuelRepository.save(b);

        for (LigneBudgetRequest ligne : req.lignes()) {
            CategorieDepense cat =
                    categorieDepenseRepository
                            .findByIdAndOrganisationId(ligne.categorieId(), orgId)
                            .orElseThrow(() -> BusinessException.notFound("CATEGORIE_ABSENTE"));
            TypeCategorie type = parseType(ligne.type());
            if (cat.getType() != type) {
                throw BusinessException.badRequest("BUDGET_LIGNE_TYPE_CATEGORIE");
            }
            LigneBudget lb = new LigneBudget();
            lb.setBudget(b);
            lb.setCategorie(cat);
            lb.setType(type);
            lb.setMontantPrevu(ligne.montantPrevu());
            b.getLignes().add(lb);
        }
        b = budgetAnnuelRepository.save(b);
        Map<String, Object> apres = snapshotBudgetCree(b);
        auditLogService.log(orgId, userId, "CREATE", "BudgetAnnuel", b.getId(), null, apres);
        return toResponse(b, orgId);
    }

    @Transactional
    public BudgetResponse modifierLigne(
            UUID budgetId, UUID ligneId, ModifierLigneBudgetRequest req, UUID orgId, UUID userId) {
        BudgetAnnuel b =
                budgetAnnuelRepository
                        .findById(budgetId)
                        .filter(x -> x.getOrganisationId().equals(orgId))
                        .orElseThrow(() -> BusinessException.notFound("BUDGET_ABSENT"));
        if (b.getStatut() == StatutBudget.CLOTURE) {
            throw BusinessException.badRequest("BUDGET_CLOTURE");
        }
        LigneBudget lb =
                ligneBudgetRepository
                        .findByIdAndBudget_Id(ligneId, budgetId)
                        .orElseThrow(() -> BusinessException.notFound("LIGNE_BUDGET_ABSENTE"));
        BigDecimal avantMontant = lb.getMontantPrevu();
        lb.setMontantPrevu(req.montantPrevu());
        ligneBudgetRepository.save(lb);
        Map<String, Object> avant = new LinkedHashMap<>();
        avant.put("budgetId", budgetId);
        avant.put("ligneId", ligneId);
        avant.put("montantPrevu", avantMontant);
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("budgetId", budgetId);
        apres.put("ligneId", ligneId);
        apres.put("montantPrevu", lb.getMontantPrevu());
        auditLogService.log(orgId, userId, "UPDATE", "LigneBudget", ligneId, avant, apres);
        return toResponse(budgetAnnuelRepository.findById(budgetId).orElseThrow(), orgId);
    }

    @Transactional
    public BudgetResponse valider(UUID budgetId, UUID orgId, UUID valideurId) {
        BudgetAnnuel b =
                budgetAnnuelRepository
                        .findById(budgetId)
                        .filter(x -> x.getOrganisationId().equals(orgId))
                        .orElseThrow(() -> BusinessException.notFound("BUDGET_ABSENT"));
        if (b.getStatut() != StatutBudget.BROUILLON) {
            throw BusinessException.badRequest("BUDGET_DEJA_VALIDE");
        }
        Map<String, Object> avantEtat = snapshotBudgetEtat(b);
        UUID ancienValideId =
                budgetAnnuelRepository
                        .findByOrganisationIdAndAnneeAndStatut(orgId, b.getAnnee(), StatutBudget.VALIDE)
                        .map(
                                ancien -> {
                                    ancien.setStatut(StatutBudget.CLOTURE);
                                    budgetAnnuelRepository.save(ancien);
                                    return ancien.getId();
                                })
                        .orElse(null);
        b.setStatut(StatutBudget.VALIDE);
        b.setDateValidation(Instant.now());
        b.setValideur(utilisateurRepository.getReferenceById(valideurId));
        budgetAnnuelRepository.save(b);
        Map<String, Object> apresEtat = snapshotBudgetEtat(b);
        if (ancienValideId != null) {
            apresEtat.put("budgetPrecedentClotureId", ancienValideId);
        }
        auditLogService.log(orgId, valideurId, "UPDATE", "BudgetAnnuel", b.getId(), avantEtat, apresEtat);
        return toResponse(b, orgId);
    }

    private static Map<String, Object> snapshotBudgetCree(BudgetAnnuel b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("annee", b.getAnnee());
        m.put("statut", b.getStatut().name());
        m.put("nbLignes", b.getLignes() != null ? b.getLignes().size() : 0);
        m.put("notes", b.getNotes());
        return m;
    }

    private static Map<String, Object> snapshotBudgetEtat(BudgetAnnuel b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("annee", b.getAnnee());
        m.put("statut", b.getStatut().name());
        m.put("dateValidation", b.getDateValidation());
        return m;
    }

    private BudgetResponse toResponse(BudgetAnnuel b, UUID orgId) {
        Organisation org =
                organisationRepository.findById(orgId).orElseThrow(() -> BusinessException.notFound("ORG_ABSENTE"));
        int seuil = org.getAlerteBudgetPct();

        List<LigneBudgetResponse> lignes = new ArrayList<>();
        BigDecimal[] tot = {
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        };

        jdbcTemplate.query(
                """
                        SELECT ligne_id, categorie_id, categorie_libelle, type::text AS type,
                               montant_prevu, montant_realise, ecart, taux_execution_pct
                        FROM v_execution_budget
                        WHERE budget_id = ?
                        """,
                rs -> {
                    while (rs.next()) {
                        UUID lid = rs.getObject("ligne_id", UUID.class);
                        UUID catId = rs.getObject("categorie_id", UUID.class);
                        String catLib = rs.getString("categorie_libelle");
                        String type = rs.getString("type");
                        BigDecimal prevu = rs.getBigDecimal("montant_prevu");
                        BigDecimal realise = rs.getBigDecimal("montant_realise");
                        BigDecimal ecart = rs.getBigDecimal("ecart");
                        BigDecimal taux = rs.getBigDecimal("taux_execution_pct");
                        if (taux == null) {
                            taux = BigDecimal.ZERO;
                        }
                        boolean alerte = taux.compareTo(BigDecimal.valueOf(seuil)) >= 0;
                        lignes.add(
                                new LigneBudgetResponse(
                                        lid, catId, catLib, type, prevu, realise, ecart, taux, alerte));
                        if ("DEPENSE".equals(type)) {
                            tot[0] = tot[0].add(prevu);
                            tot[1] = tot[1].add(realise);
                        } else {
                            tot[2] = tot[2].add(prevu);
                            tot[3] = tot[3].add(realise);
                        }
                    }
                    return null;
                },
                b.getId());

        LocalDateTime dateVal =
                b.getDateValidation() == null
                        ? null
                        : LocalDateTime.ofInstant(b.getDateValidation(), ZoneId.systemDefault());

        return new BudgetResponse(
                b.getId(),
                b.getAnnee(),
                b.getStatut().name(),
                dateVal,
                lignes,
                tot[0].setScale(2, RoundingMode.HALF_UP),
                tot[1].setScale(2, RoundingMode.HALF_UP),
                tot[2].setScale(2, RoundingMode.HALF_UP),
                tot[3].setScale(2, RoundingMode.HALF_UP));
    }

    private static TypeCategorie parseType(String s) {
        try {
            return TypeCategorie.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            throw BusinessException.badRequest("BUDGET_TYPE_INVALIDE");
        }
    }
}

package com.app.modules.rapports.service;

import com.app.modules.budget.entity.StatutBudget;
import com.app.modules.budget.repository.BudgetAnnuelRepository;
import com.app.modules.finance.entity.StatutFacture;
import com.app.modules.finance.repository.FactureRepository;
import com.app.modules.finance.service.FactureService;
import com.app.modules.inventaire.entity.EtatBien;
import com.app.modules.inventaire.repository.BienMaterielRepository;
import com.app.modules.missions.entity.FraisMission;
import com.app.modules.missions.entity.Mission;
import com.app.modules.missions.entity.StatutFrais;
import com.app.modules.missions.entity.StatutMission;
import com.app.modules.missions.repository.FraisMissionRepository;
import com.app.modules.missions.repository.MissionRepository;
import com.app.modules.missions.service.MissionService;
import com.app.modules.rapports.dto.todo.QuickActionRequest;
import com.app.modules.rapports.dto.todo.QuickActionResponse;
import com.app.modules.rapports.dto.todo.TodoActionGroup;
import com.app.modules.rapports.dto.todo.TodoActionItem;
import com.app.modules.rapports.dto.todo.TodoDashboardResponse;
import com.app.modules.rapports.dto.todo.TodoSection;
import com.app.modules.rh.dto.CongeValidationRequest;
import com.app.modules.rh.dto.MarquerPayeRequest;
import com.app.modules.rh.entity.CongeAbsence;
import com.app.modules.rh.entity.StatutConge;
import com.app.modules.rh.entity.StatutPaie;
import com.app.modules.rh.repository.CongeRepository;
import com.app.modules.rh.repository.PaiementSalaireRepository;
import com.app.modules.rh.service.CongeService;
import com.app.modules.rh.service.PaieService;
import com.app.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TodoDashboardService {

    private final CongeRepository congeRepository;
    private final FactureRepository factureRepository;
    private final PaiementSalaireRepository paiementSalaireRepository;
    private final BudgetAnnuelRepository budgetAnnuelRepository;
    private final BienMaterielRepository bienMaterielRepository;
    private final MissionRepository missionRepository;
    private final FraisMissionRepository fraisMissionRepository;
    private final EntityManager entityManager;

    private final CongeService congeService;
    private final PaieService paieService;
    private final MissionService missionService;

    @Transactional(readOnly = true)
    public TodoDashboardResponse getTodoDashboard(UUID orgId, String role, UUID userId) {
        String r = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        List<TodoSection> sections = new ArrayList<>();

        if ("RH".equals(r)) {
            sections.add(buildSectionRh(orgId, userId));
        } else if ("FINANCIER".equals(r)) {
            sections.add(buildSectionFinancier(orgId));
        } else if ("ADMIN".equals(r)) {
            sections.add(buildSectionRh(orgId, userId));
            sections.add(buildSectionFinancier(orgId));
            sections.add(buildSectionAdmin(orgId));
        } else if ("LOGISTIQUE".equals(r)) {
            sections.add(buildSectionLogistique(orgId));
        } else {
            // EMPLOYE or unknown -> empty widget
            sections = List.of();
        }

        long total = 0;
        long urgent = 0;
        for (TodoSection s : sections) {
            total += s.totalActions();
            urgent += s.actionsUrgentes();
        }

        Map<String, Long> comptages = buildComptagesRapides(orgId, r, sections);

        return new TodoDashboardResponse(
                LocalDateTime.now(),
                r,
                total,
                urgent,
                sections,
                comptages
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getComptages(UUID orgId, String role, UUID userId) {
        String r = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        Map<String, Long> out = new LinkedHashMap<>();

        if ("EMPLOYE".equals(r) || r.isBlank()) {
            return Map.of("total", 0L);
        }

        // RH counts
        if ("RH".equals(r) || "ADMIN".equals(r)) {
            Map<String, Object> rh = selectOneRow(
                    "SELECT conges_a_valider, conges_urgents, salaires_mois_courant FROM v_todo_rh WHERE organisation_id = :orgId",
                    Map.of("orgId", orgId));
            out.put("conges_a_valider", asLong(rh.get("conges_a_valider")));
            out.put("conges_urgents", asLong(rh.get("conges_urgents")));
            out.put("salaires_mois_courant", asLong(rh.get("salaires_mois_courant")));
            if (tableExists("missions")) {
                out.put("missions_a_approuver", countMissions(orgId));
                if (tableExists("frais_mission")) {
                    out.put("frais_a_valider", countFrais(orgId, StatutFrais.SOUMIS));
                }
            }
            if (tableExists("echeances_rh")) {
                long e =
                        asLong(
                                entityManager.createNativeQuery(
                                                "SELECT COUNT(*) FROM echeances_rh WHERE organisation_id = :orgId AND statut IN ('EN_ALERTE','ACTION_REQUISE')")
                                        .setParameter("orgId", orgId)
                                        .getSingleResult());
                out.put("echeances_rh", e);
            }
        }

        // FINANCIER counts
        if ("FINANCIER".equals(r) || "ADMIN".equals(r)) {
            Map<String, Object> fin = selectOneRow(
                    "SELECT factures_a_payer, factures_en_retard, salaires_a_verser FROM v_todo_financier WHERE organisation_id = :orgId",
                    Map.of("orgId", orgId));
            out.put("factures_a_payer", asLong(fin.get("factures_a_payer")));
            out.put("factures_en_retard", asLong(fin.get("factures_en_retard")));
            out.put("salaires_a_verser", asLong(fin.get("salaires_a_verser")));
            if (tableExists("frais_mission")) {
                out.put("frais_a_rembourser", countFrais(orgId, StatutFrais.VALIDE));
            }
            boolean budgetDraft =
                    budgetAnnuelRepository.existsByOrganisationIdAndAnneeAndStatutIn(
                            orgId, LocalDate.now().getYear(), List.of(StatutBudget.BROUILLON));
            out.put("budget_a_valider", budgetDraft ? 1L : 0L);
        }

        // ADMIN counts (only if ADMIN)
        if ("ADMIN".equals(r)) {
            Map<String, Object> admin = selectOneRow(
                    "SELECT factures_retard_critique, budgets_a_valider, biens_defaillants FROM v_todo_admin WHERE organisation_id = :orgId",
                    Map.of("orgId", orgId));
            out.put("factures_retard_critique", asLong(admin.get("factures_retard_critique")));
            out.put("budgets_a_valider", asLong(admin.get("budgets_a_valider")));
            out.put("biens_defaillants", asLong(admin.get("biens_defaillants")));
        }

        if ("LOGISTIQUE".equals(r)) {
            out.put("biens_defaillants", bienMaterielRepository.countByOrganisationIdAndEtat(orgId, EtatBien.DEFAILLANT));
        }

        long total = out.values().stream().mapToLong(Long::longValue).sum();
        out.put("total", total);
        return out;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Sections
    // ──────────────────────────────────────────────────────────────────────────

    TodoSection buildSectionRh(UUID orgId, UUID userId) {
        List<TodoActionGroup> groups = new ArrayList<>();

        // Counts via view
        Map<String, Object> counts = selectOneRow(
                "SELECT conges_a_valider, conges_urgents, salaires_mois_courant FROM v_todo_rh WHERE organisation_id = :orgId",
                Map.of("orgId", orgId));

        long congesCount = asLong(counts.get("conges_a_valider"));
        long congesUrg = asLong(counts.get("conges_urgents"));

        // Top 5 conges
        List<CongeAbsence> congesTop = congeRepository
                .findByOrganisationIdAndStatutOrderByCreatedAtAsc(orgId, StatutConge.EN_ATTENTE, org.springframework.data.domain.PageRequest.of(0, 5))
                .getContent();

        List<TodoActionItem> congesItems = congesTop.stream().map(c -> {
            String salNom = c.getSalarie() != null ? ((c.getSalarie().getNom() + " " + c.getSalarie().getPrenom()).trim()) : "";
            String service = c.getSalarie() != null ? nz(c.getSalarie().getService()) : "";
            LocalDateTime created = toLdt(c.getCreatedAt());
            long ageDays = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), LocalDate.now()) : 0;
            String urgence = ageDays > 5 ? "URGENT" : "NORMAL";
            return new TodoActionItem(
                    c.getId(),
                    "CONGE",
                    c.getTypeConge().name() + " — " + salNom + " (" + fmt(c.getDateDebut()) + " au " + fmt(c.getDateFin()) + ")",
                    service + " · Soumis " + humanizeDelay(created),
                    c.getStatut() != null ? c.getStatut().name() : null,
                    urgence,
                    "/rh/conges/" + c.getId(),
                    created,
                    null,
                    json(Map.of(
                            "nbJours", c.getNbJours(),
                            "type", c.getTypeConge() != null ? c.getTypeConge().name() : null,
                            "salarieId", c.getSalarie() != null ? c.getSalarie().getId() : null
                    ))
            );
        }).toList();

        groups.add(new TodoActionGroup(
                "CONGES_A_VALIDER",
                "Congés en attente de validation",
                "🗓️",
                congesCount,
                congesUrg,
                "/rh/conges?statut=EN_ATTENTE",
                congesItems
        ));

        // Missions (optional module)
        if (tableExists("missions")) {
            List<Mission> missionsTop = missionRepository.findByOrganisationIdAndStatutOrderByCreatedAtAsc(orgId, StatutMission.SOUMISE, org.springframework.data.domain.PageRequest.of(0, 5)).getContent();
            long missionsCount = countMissions(orgId);
            long missionsUrg = missionsTop.stream().map(Mission::getCreatedAt).map(this::toLdt).filter(x -> x != null && ChronoUnit.DAYS.between(x.toLocalDate(), LocalDate.now()) > 3).count();
            List<TodoActionItem> missionItems = missionsTop.stream().map(m -> {
                String salNom = m.getSalarie() != null ? ((m.getSalarie().getNom() + " " + m.getSalarie().getPrenom()).trim()) : "";
                LocalDateTime created = toLdt(m.getCreatedAt());
                long ageDays = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), LocalDate.now()) : 0;
                String urgence = ageDays > 3 ? "URGENT" : "NORMAL";
                return new TodoActionItem(
                        m.getId(),
                        "MISSION",
                        "Mission : " + nz(m.getTitre()) + " — " + salNom,
                        nz(m.getDestination()) + " · " + humanizeDelay(created),
                        m.getStatut() != null ? m.getStatut().name() : null,
                        urgence,
                        "/missions/" + m.getId(),
                        created,
                        null,
                        json(Map.of("destination", m.getDestination()))
                );
            }).toList();

            groups.add(new TodoActionGroup(
                    "MISSIONS_A_APPROUVER",
                    "Missions soumises à approuver",
                    "🧳",
                    missionsCount,
                    missionsUrg,
                    "/missions?statut=SOUMISE",
                    missionItems
            ));

            // Frais à valider
            if (tableExists("frais_mission")) {
                long fraisCount = countFrais(orgId, StatutFrais.SOUMIS);
                List<FraisMission> fraisTop = fraisMissionRepository.findTop5ByMission_OrganisationIdAndStatutOrderByCreatedAtAsc(orgId, StatutFrais.SOUMIS);
                long fraisUrg = fraisTop.stream().map(FraisMission::getCreatedAt).map(this::toLdt).filter(x -> x != null && ChronoUnit.DAYS.between(x.toLocalDate(), LocalDate.now()) > 3).count();
                List<TodoActionItem> fraisItems = fraisTop.stream().map(f -> {
                    Mission m = f.getMission();
                    String salNom = (m != null && m.getSalarie() != null) ? ((m.getSalarie().getNom() + " " + m.getSalarie().getPrenom()).trim()) : "";
                    LocalDateTime created = toLdt(f.getCreatedAt());
                    long ageDays = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), LocalDate.now()) : 0;
                    String urgence = ageDays > 3 ? "URGENT" : "NORMAL";
                    return new TodoActionItem(
                            f.getId(),
                            "FRAIS",
                            nz(f.getTypeFrais()) + " — " + nz(f.getDescription()) + " (" + salNom + ")",
                            humanizeDelay(created),
                            f.getStatut() != null ? f.getStatut().name() : null,
                            urgence,
                            m != null ? ("/missions/" + m.getId() + "#frais") : "/missions",
                            created,
                            null,
                            json(Map.of("missionId", m != null ? m.getId() : null))
                    );
                }).toList();

                groups.add(new TodoActionGroup(
                        "FRAIS_A_VALIDER",
                        "Frais en attente de validation",
                        "🧾",
                        fraisCount,
                        fraisUrg,
                        "/missions",
                        fraisItems
                ));
            }
        }

        // Échéances RH (optional module)
        if (tableExists("echeances_rh")) {
            // Keep count quick via native count to avoid extra mapping here.
            long count = asLong(entityManager.createNativeQuery(
                            "SELECT COUNT(*) FROM echeances_rh WHERE organisation_id = :orgId AND statut IN ('EN_ALERTE','ACTION_REQUISE')")
                    .setParameter("orgId", orgId)
                    .getSingleResult());
            @SuppressWarnings("unchecked")
            List<Object[]> top =
                    entityManager.createNativeQuery(
                                    "SELECT id, titre, statut, date_echeance, created_at FROM echeances_rh " +
                                            "WHERE organisation_id = :orgId AND statut IN ('EN_ALERTE','ACTION_REQUISE') " +
                                            "ORDER BY date_echeance ASC LIMIT 5")
                            .setParameter("orgId", orgId)
                            .getResultList();
            List<TodoActionItem> items = top.stream().map(r -> {
                UUID id = (UUID) r[0];
                String titre = (String) r[1];
                String statut = r[2] != null ? String.valueOf(r[2]) : null;
                LocalDate date = r[3] != null ? ((java.sql.Date) r[3]).toLocalDate() : null;
                LocalDateTime created = r[4] != null ? LocalDateTime.ofInstant(((java.sql.Timestamp) r[4]).toInstant(), ZoneId.systemDefault()) : null;
                String urgence = "ACTION_REQUISE".equalsIgnoreCase(statut) ? "CRITIQUE" : "URGENT";
                return new TodoActionItem(
                        id,
                        "ECHEANCE_RH",
                        nz(titre),
                        date != null ? ("Échéance: " + fmt(date)) : null,
                        statut,
                        urgence,
                        "/rh/contrats/echeances/" + id,
                        created,
                        date != null ? date.atStartOfDay() : null,
                        json(Map.of("statut", statut))
                );
            }).toList();

            long urgentCount = items.stream().filter(i -> "CRITIQUE".equals(i.niveauUrgence()) || "URGENT".equals(i.niveauUrgence())).count();
            groups.add(new TodoActionGroup(
                    "ECHEANCES_RH_URGENTES",
                    "Échéances RH urgentes",
                    "⏰",
                    count,
                    urgentCount,
                    "/rh/contrats",
                    items
            ));
        }

        long total = groups.stream().mapToLong(TodoActionGroup::count).sum();
        long urg = groups.stream().mapToLong(TodoActionGroup::countUrgent).sum();
        return new TodoSection("Ressources Humaines", "👥", total, urg, groups);
    }

    TodoSection buildSectionFinancier(UUID orgId) {
        List<TodoActionGroup> groups = new ArrayList<>();

        Map<String, Object> counts = selectOneRow(
                "SELECT factures_a_payer, factures_en_retard, montant_total_a_payer, salaires_a_verser, montant_salaires_a_verser " +
                        "FROM v_todo_financier WHERE organisation_id = :orgId",
                Map.of("orgId", orgId));

        long facturesCount = asLong(counts.get("factures_a_payer"));
        long facturesRetard = asLong(counts.get("factures_en_retard"));
        // Top 5 factures oldest
        var facturesTop = factureRepository.findTop5ByOrganisationIdAndStatutOrderByDateFactureAsc(orgId, StatutFacture.A_PAYER);
        List<TodoActionItem> factItems = facturesTop.stream().map(f -> {
            long age = f.getDateFacture() != null ? ChronoUnit.DAYS.between(f.getDateFacture(), LocalDate.now()) : 0;
            String urgence = age > 30 ? "CRITIQUE" : (age > 15 ? "URGENT" : "NORMAL");
            return new TodoActionItem(
                    f.getId(),
                    "FACTURE",
                    nz(f.getReference()) + " — " + nz(f.getFournisseur()),
                    money(f.getMontantTtc(), f.getDevise()) + " · " + nz(f.getCategorie() != null ? f.getCategorie().getLibelle() : null) +
                            " · émise le " + fmt(f.getDateFacture()),
                    f.getStatut() != null ? f.getStatut().name() : null,
                    urgence,
                    "/finance/factures/" + f.getId(),
                    toLdt(f.getCreatedAt()),
                    null,
                    json(Map.of(
                            "montantTtc", f.getMontantTtc(),
                            "devise", f.getDevise(),
                            "reference", f.getReference(),
                            "retardJours", age
                    ))
            );
        }).toList();

        groups.add(new TodoActionGroup(
                "FACTURES_A_PAYER",
                "Factures à payer",
                "🧾",
                facturesCount,
                facturesRetard,
                "/finance/factures?statut=A_PAYER",
                factItems
        ));

        // Paie mois courant
        int mois = LocalDate.now().getMonthValue();
        int annee = LocalDate.now().getYear();
        long salairesCount = asLong(counts.get("salaires_a_verser"));

        var paiesTop = paiementSalaireRepository.findTop5ByOrganisationIdAndStatutAndAnneeAndMoisOrderBySalarie_NomAscSalarie_PrenomAsc(
                orgId, StatutPaie.EN_ATTENTE, annee, mois);
        boolean afterJ5 = LocalDate.now().getDayOfMonth() >= 6;
        List<TodoActionItem> paieItems = paiesTop.stream().map(p -> {
            String urgence = afterJ5 ? "URGENT" : "NORMAL";
            String nom = p.getSalarie() != null ? ((p.getSalarie().getNom() + " " + p.getSalarie().getPrenom()).trim()) : "";
            String service = p.getSalarie() != null ? nz(p.getSalarie().getService()) : "";
            return new TodoActionItem(
                    p.getId(),
                    "PAIE",
                    nom + " — " + nz(p.getSalarie() != null ? p.getSalarie().getMatricule() : null),
                    money(p.getMontant(), p.getDevise()) + " · " + service + (p.getModePaiement() != null ? " · " + p.getModePaiement() : ""),
                    p.getStatut() != null ? p.getStatut().name() : null,
                    urgence,
                    p.getSalarie() != null ? ("/rh/paie/" + p.getSalarie().getId() + "/" + annee) : "/rh/paie",
                    toLdt(p.getCreatedAt()),
                    null,
                    json(Map.of("montant", p.getMontant(), "mois", p.getMois(), "annee", p.getAnnee(), "service", service))
            );
        }).toList();

        groups.add(new TodoActionGroup(
                "SALAIRES_A_VERSER",
                "Salaires à verser (mois courant)",
                "💸",
                salairesCount,
                afterJ5 ? salairesCount : 0,
                "/rh/paie?mois=" + mois + "&annee=" + annee,
                paieItems
        ));

        // Frais à rembourser (optional)
        if (tableExists("frais_mission")) {
            long count = countFrais(orgId, StatutFrais.VALIDE);
            List<FraisMission> top = fraisMissionRepository.findTop5ByMission_OrganisationIdAndStatutOrderByCreatedAtAsc(orgId, StatutFrais.VALIDE);
            long urg = top.stream().map(FraisMission::getCreatedAt).map(this::toLdt).filter(x -> x != null && ChronoUnit.DAYS.between(x.toLocalDate(), LocalDate.now()) > 3).count();
            List<TodoActionItem> items = top.stream().map(f -> {
                Mission m = f.getMission();
                String salNom = (m != null && m.getSalarie() != null) ? ((m.getSalarie().getNom() + " " + m.getSalarie().getPrenom()).trim()) : "";
                LocalDateTime created = toLdt(f.getCreatedAt());
                long ageDays = created != null ? ChronoUnit.DAYS.between(created.toLocalDate(), LocalDate.now()) : 0;
                String urgence = ageDays > 3 ? "URGENT" : "NORMAL";
                return new TodoActionItem(
                        f.getId(),
                        "FRAIS",
                        nz(f.getTypeFrais()) + " — " + nz(f.getDescription()) + " (" + salNom + ")",
                        humanizeDelay(created),
                        f.getStatut() != null ? f.getStatut().name() : null,
                        urgence,
                        m != null ? ("/missions/" + m.getId() + "#frais") : "/missions",
                        created,
                        null,
                        json(Map.of("missionId", m != null ? m.getId() : null))
                );
            }).toList();
            groups.add(new TodoActionGroup(
                    "FRAIS_A_REMBOURSER",
                    "Frais validés à rembourser",
                    "💳",
                    count,
                    urg,
                    "/missions",
                    items
            ));
        }

        // Budget à valider (for financier too per spec)
        var bOpt = budgetAnnuelRepository.findByOrganisationIdAndAnneeAndStatut(orgId, LocalDate.now().getYear(), StatutBudget.BROUILLON);
        if (bOpt.isPresent()) {
            var b = bOpt.get();
            groups.add(new TodoActionGroup(
                    "BUDGET_A_VALIDER",
                    "Budget en attente de validation",
                    "📊",
                    1,
                    1,
                    "/budget",
                    List.of(new TodoActionItem(
                            b.getId(),
                            "BUDGET",
                            "Budget " + b.getAnnee() + " en attente de validation",
                            "Créé " + humanizeDelay(toLdt(b.getCreatedAt())),
                            b.getStatut() != null ? b.getStatut().name() : null,
                            "URGENT",
                            "/budget",
                            toLdt(b.getCreatedAt()),
                            null,
                            json(Map.of("annee", b.getAnnee()))
                    ))
            ));
        }

        long total = groups.stream().mapToLong(TodoActionGroup::count).sum();
        long urg = groups.stream().mapToLong(TodoActionGroup::countUrgent).sum();
        return new TodoSection("Finance", "💰", total, urg, groups);
    }

    TodoSection buildSectionAdmin(UUID orgId) {
        List<TodoActionGroup> groups = new ArrayList<>();

        Map<String, Object> counts = selectOneRow(
                "SELECT factures_retard_critique, budgets_a_valider, biens_defaillants FROM v_todo_admin WHERE organisation_id = :orgId",
                Map.of("orgId", orgId));

        // factures retard critique
        long crit = asLong(counts.get("factures_retard_critique"));
        var topCrit = factureRepository.findTop5ByOrganisationIdAndStatutAndDateFactureBeforeOrderByDateFactureAsc(
                orgId, StatutFacture.A_PAYER, LocalDate.now().minusDays(45));
        List<TodoActionItem> critItems = topCrit.stream().map(f -> {
            long age = f.getDateFacture() != null ? ChronoUnit.DAYS.between(f.getDateFacture(), LocalDate.now()) : 0;
            return new TodoActionItem(
                    f.getId(),
                    "FACTURE",
                    nz(f.getReference()) + " — " + nz(f.getFournisseur()),
                    money(f.getMontantTtc(), f.getDevise()) + " · En retard de " + age + " jours",
                    f.getStatut() != null ? f.getStatut().name() : null,
                    "CRITIQUE",
                    "/finance/factures/" + f.getId(),
                    toLdt(f.getCreatedAt()),
                    null,
                    json(Map.of("retardJours", age))
            );
        }).toList();
        groups.add(new TodoActionGroup(
                "FACTURES_RETARD_CRITIQUE",
                "Factures en retard critique",
                "🚨",
                crit,
                crit,
                "/finance/factures?statut=A_PAYER",
                critItems
        ));

        // docs expirants (optional contracts)
        if (tableExists("titres_sejour") && tableExists("formations_obligatoires")) {
            // Use native minimal queries to avoid adding entity dependency here.
            @SuppressWarnings("unchecked")
            List<Object[]> docs =
                    entityManager.createNativeQuery(
                                    "SELECT s.id as salarie_id, 'TITRE_SEJOUR' as type, t.type_document as label, t.date_expiration as exp " +
                                            "FROM titres_sejour t JOIN salaries s ON s.id = t.salarie_id " +
                                            "WHERE t.organisation_id = :orgId AND t.date_expiration <= CURRENT_DATE + INTERVAL '30 days' " +
                                            "UNION ALL " +
                                            "SELECT s.id as salarie_id, 'FORMATION' as type, f.intitule as label, f.date_expiration as exp " +
                                            "FROM formations_obligatoires f JOIN salaries s ON s.id = f.salarie_id " +
                                            "WHERE f.organisation_id = :orgId AND f.date_expiration <= CURRENT_DATE + INTERVAL '30 days' " +
                                            "ORDER BY exp ASC LIMIT 5")
                            .setParameter("orgId", orgId)
                            .getResultList();
            List<TodoActionItem> items = docs.stream().map(r -> {
                UUID salarieId = (UUID) r[0];
                String type = String.valueOf(r[1]);
                String label = String.valueOf(r[2]);
                LocalDate exp = r[3] != null ? ((java.sql.Date) r[3]).toLocalDate() : null;
                long days = exp != null ? ChronoUnit.DAYS.between(LocalDate.now(), exp) : 999;
                String urgence = days < 7 ? "CRITIQUE" : "URGENT";
                return new TodoActionItem(
                        salarieId,
                        type,
                        label,
                        exp != null ? ("Expire le " + fmt(exp) + " (" + days + " jours)") : null,
                        null,
                        urgence,
                        "/rh/contrats/salaries/" + salarieId,
                        null,
                        exp != null ? exp.atStartOfDay() : null,
                        json(Map.of("joursRestants", days))
                );
            }).toList();
            groups.add(new TodoActionGroup(
                    "DOCS_EXPIRANTS",
                    "Documents expirants",
                    "📌",
                    items.size(),
                    items.stream().filter(i -> !"NORMAL".equals(i.niveauUrgence())).count(),
                    "/rh/contrats",
                    items
            ));
        }

        // biens defaillants
        long biens = asLong(counts.get("biens_defaillants"));
        var biensTop = bienMaterielRepository.findTop5ByOrganisationIdAndEtatOrderByUpdatedAtDesc(orgId, EtatBien.DEFAILLANT);
        List<TodoActionItem> bienItems = biensTop.stream().map(b -> new TodoActionItem(
                b.getId(),
                "BIEN",
                nz(b.getLibelle()) + " — " + nz(b.getCodeInventaire()),
                nz(b.getCategorie()) + " · " + nz(b.getLocalisation()),
                b.getEtat() != null ? b.getEtat().name() : null,
                "URGENT",
                "/inventaire",
                toLdt(b.getCreatedAt()),
                null,
                json(Map.of("categorie", b.getCategorie(), "localisation", b.getLocalisation()))
        )).toList();
        groups.add(new TodoActionGroup(
                "BIENS_DEFAILLANTS",
                "Biens matériels défaillants",
                "🧰",
                biens,
                biens,
                "/inventaire",
                bienItems
        ));

        // budgets
        long budgetsCount = asLong(counts.get("budgets_a_valider"));
        if (budgetsCount > 0) {
            groups.add(new TodoActionGroup(
                    "BUDGETS_A_VALIDER",
                    "Budgets à valider",
                    "📊",
                    budgetsCount,
                    budgetsCount,
                    "/budget",
                    List.of()
            ));
        }

        long total = groups.stream().mapToLong(TodoActionGroup::count).sum();
        long urg = groups.stream().mapToLong(TodoActionGroup::countUrgent).sum();
        return new TodoSection("Administration", "⚙️", total, urg, groups);
    }

    TodoSection buildSectionLogistique(UUID orgId) {
        List<TodoActionGroup> groups = new ArrayList<>();
        var biens = bienMaterielRepository.countByOrganisationIdAndEtat(orgId, EtatBien.DEFAILLANT);
        var biensTop = bienMaterielRepository.findTop5ByOrganisationIdAndEtatOrderByUpdatedAtDesc(orgId, EtatBien.DEFAILLANT);
        List<TodoActionItem> items = biensTop.stream().map(b -> new TodoActionItem(
                b.getId(),
                "BIEN",
                nz(b.getLibelle()) + " — " + nz(b.getCodeInventaire()),
                nz(b.getCategorie()) + " · " + nz(b.getLocalisation()),
                b.getEtat() != null ? b.getEtat().name() : null,
                "URGENT",
                "/inventaire",
                toLdt(b.getCreatedAt()),
                null,
                json(Map.of("categorie", b.getCategorie(), "localisation", b.getLocalisation()))
        )).toList();
        groups.add(new TodoActionGroup(
                "BIENS_DEFAILLANTS",
                "Biens matériels défaillants",
                "🧰",
                biens,
                biens,
                "/inventaire",
                items
        ));
        return new TodoSection("Logistique", "🏭", biens, biens, groups);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Quick actions
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public QuickActionResponse executerActionRapide(UUID itemId, String itemType, QuickActionRequest req, UUID orgId, UUID userId) {
        String action = req.typeAction() == null ? "" : req.typeAction().trim().toUpperCase(Locale.ROOT);
        switch (action) {
            case "VALIDER_CONGE" -> {
                congeService.valider(itemId, userId, orgId);
                long left = congeRepository.countByOrganisationIdAndStatut(orgId, StatutConge.EN_ATTENTE);
                return new QuickActionResponse(true, "Congé validé", "VALIDE", left);
            }
            case "REJETER_CONGE" -> {
                String motif = req.commentaire() == null ? "" : req.commentaire().trim();
                if (motif.isBlank()) throw BusinessException.badRequest("TODO_ACTION_IMPOSSIBLE");
                congeService.rejeter(itemId, new CongeValidationRequest(motif), orgId);
                long left = congeRepository.countByOrganisationIdAndStatut(orgId, StatutConge.EN_ATTENTE);
                return new QuickActionResponse(true, "Congé rejeté", "REJETE", left);
            }
            case "MARQUER_PAIE_PAYEE" -> {
                paieService.marquerPaye(itemId, new MarquerPayeRequest(LocalDate.now(), "VIREMENT", req.commentaire()), orgId);
                return new QuickActionResponse(true, "Paie marquée payée", "PAYE", 0);
            }
            case "APPROUVER_MISSION" -> {
                if (!tableExists("missions")) throw BusinessException.badRequest("TODO_ACTION_IMPOSSIBLE");
                missionService.approuver(itemId, userId, null, orgId);
                return new QuickActionResponse(true, "Mission approuvée", "APPROUVEE", 0);
            }
            case "REJETER_MISSION" -> {
                if (!tableExists("missions")) throw BusinessException.badRequest("TODO_ACTION_IMPOSSIBLE");
                missionService.refuser(itemId, req.commentaire(), orgId);
                return new QuickActionResponse(true, "Mission rejetée", "ANNULEE", 0);
            }
            case "MARQUER_FACTURE_PAYEE" -> {
                // FactureService does not expose PAYE transition directly (requires payment flow).
                throw BusinessException.badRequest("TODO_ACTION_IMPOSSIBLE");
            }
            default -> throw BusinessException.badRequest("TODO_ACTION_IMPOSSIBLE");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers (counts, table existence, mapping)
    // ──────────────────────────────────────────────────────────────────────────

    private Map<String, Long> buildComptagesRapides(UUID orgId, String role, List<TodoSection> sections) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (TodoSection s : sections) {
            for (TodoActionGroup g : s.groupes()) {
                String k = g.categorie().toLowerCase(Locale.ROOT);
                out.put(k, g.count());
            }
        }
        out.put("total", sections.stream().mapToLong(TodoSection::totalActions).sum());
        return out;
    }

    private boolean tableExists(String tableName) {
        return tableExistsInSchema("public", tableName);
    }

    private boolean tableExistsInSchema(String schema, String tableName) {
        try {
            Object o = entityManager.createNativeQuery(
                            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = :schema AND table_name = :t")
                    .setParameter("schema", schema)
                    .setParameter("t", tableName)
                    .getSingleResult();
            return asLong(o) > 0;
        } catch (PersistenceException ex) {
            return false;
        }
    }

    private long countMissions(UUID orgId) {
        try {
            return missionRepository.countByOrganisationIdAndStatut(orgId, StatutMission.SOUMISE);
        } catch (Exception ex) {
            return 0;
        }
    }

    private long countFrais(UUID orgId, StatutFrais statut) {
        try {
            return fraisMissionRepository.countByMission_OrganisationIdAndStatut(orgId, statut);
        } catch (Exception ex) {
            return 0;
        }
    }

    private Map<String, Object> selectOneRow(String hqlOrNative, Map<String, Object> params) {
        var q = entityManager.createNativeQuery(hqlOrNative, jakarta.persistence.Tuple.class);
        for (var e : params.entrySet()) {
            q.setParameter(e.getKey(), e.getValue());
        }
        @SuppressWarnings("unchecked")
        List<jakarta.persistence.Tuple> tuples = q.getResultList();
        if (tuples == null || tuples.isEmpty()) return Map.of();
        var t = tuples.getFirst();
        Map<String, Object> out = new LinkedHashMap<>();
        for (var el : t.getElements()) {
            out.put(el.getAlias(), t.get(el));
        }
        return out;
    }

    private static long asLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(o));
    }

    private static BigDecimal asBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static String fmt(LocalDate d) {
        if (d == null) return "";
        return d.toString();
    }

    private static String nz(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private LocalDateTime toLdt(Instant i) {
        if (i == null) return null;
        return LocalDateTime.ofInstant(i, ZoneId.systemDefault());
    }

    private static String money(BigDecimal v, String devise) {
        if (v == null) return "0";
        String d = devise == null ? "" : devise;
        return v.stripTrailingZeros().toPlainString() + (d.isBlank() ? "" : (" " + d));
    }

    private static String json(Map<String, Object> m) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    String humanizeDelay(LocalDateTime dt) {
        if (dt == null) return "";
        long minutes = ChronoUnit.MINUTES.between(dt, LocalDateTime.now());
        if (minutes < 60) return "il y a " + minutes + " minutes";
        long hours = ChronoUnit.HOURS.between(dt, LocalDateTime.now());
        if (hours < 24) return "il y a " + hours + " heures";
        long days = ChronoUnit.DAYS.between(dt.toLocalDate(), LocalDate.now());
        if (days < 14) return "il y a " + days + " jours";
        long weeks = days / 7;
        return "il y a " + weeks + " semaines";
    }
}


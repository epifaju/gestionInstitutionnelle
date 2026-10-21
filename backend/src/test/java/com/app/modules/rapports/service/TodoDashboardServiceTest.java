package com.app.modules.rapports.service;

import com.app.modules.budget.repository.BudgetAnnuelRepository;
import com.app.modules.finance.entity.Facture;
import com.app.modules.finance.entity.StatutFacture;
import com.app.modules.finance.repository.FactureRepository;
import com.app.modules.inventaire.repository.BienMaterielRepository;
import com.app.modules.missions.repository.FraisMissionRepository;
import com.app.modules.missions.repository.MissionRepository;
import com.app.modules.missions.service.MissionService;
import com.app.modules.rapports.dto.todo.QuickActionRequest;
import com.app.modules.rh.entity.CongeAbsence;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutConge;
import com.app.modules.rh.entity.TypeConge;
import com.app.modules.rh.repository.CongeRepository;
import com.app.modules.rh.repository.PaiementSalaireRepository;
import com.app.modules.rh.service.CongeService;
import com.app.modules.rh.service.PaieService;
import com.app.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class TodoDashboardServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID userId = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Mock private CongeRepository congeRepository;
    @Mock private FactureRepository factureRepository;
    @Mock private PaiementSalaireRepository paiementSalaireRepository;
    @Mock private BudgetAnnuelRepository budgetAnnuelRepository;
    @Mock private BienMaterielRepository bienMaterielRepository;
    @Mock private MissionRepository missionRepository;
    @Mock private FraisMissionRepository fraisMissionRepository;
    @Mock private EntityManager entityManager;
    @Mock private CongeService congeService;
    @Mock private PaieService paieService;
    @Mock private MissionService missionService;

    @InjectMocks
    private TodoDashboardService service;

    private static Tuple tupleOf(Map<String, Object> values) {
        Tuple t = mock(Tuple.class);
        List<TupleElement<?>> els = new java.util.ArrayList<>();
        for (String alias : values.keySet()) {
            @SuppressWarnings("unchecked")
            TupleElement<Object> el = mock(TupleElement.class);
            when(el.getAlias()).thenReturn(alias);
            els.add(el);
        }
        when(t.getElements()).thenReturn(els);
        lenient().when(t.get(any(TupleElement.class))).thenAnswer(inv -> {
            TupleElement<?> el = inv.getArgument(0);
            return values.get(el.getAlias());
        });
        return t;
    }

    private static Query queryReturningTuple(Tuple tuple) {
        Query q = mock(Query.class);
        lenient().when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.getResultList()).thenReturn(List.of(tuple));
        return q;
    }

    private static Query tableExistsQuery(Map<String, Long> tableCounts) {
        // In these unit tests we only simulate "tables absent" (0) to validate optional-module behavior.
        // Using RETURNS_SELF avoids stubbing the many overloaded setParameter(...) methods.
        Query q = mock(Query.class, withSettings().defaultAnswer(org.mockito.Mockito.RETURNS_SELF));
        when(q.getSingleResult()).thenAnswer(inv -> 0L);
        return q;
    }

    private void stubViews(Map<String, Object> rh, Map<String, Object> fin, Map<String, Object> admin) {
        Query qRh = rh != null ? queryReturningTuple(tupleOf(rh)) : null;
        Query qFin = fin != null ? queryReturningTuple(tupleOf(fin)) : null;
        Query qAdmin = admin != null ? queryReturningTuple(tupleOf(admin)) : null;

        when(entityManager.createNativeQuery(anyString(), eq(Tuple.class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql != null && sql.contains("FROM v_todo_rh") && qRh != null) return qRh;
            if (sql != null && sql.contains("FROM v_todo_financier") && qFin != null) return qFin;
            if (sql != null && sql.contains("FROM v_todo_admin") && qAdmin != null) return qAdmin;
            return queryReturningTuple(tupleOf(Map.of()));
        });
    }

    private void stubTableExists(Map<String, Long> tableCounts) {
        when(entityManager.createNativeQuery(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql != null && sql.contains("information_schema.tables")) {
                return tableExistsQuery(tableCounts);
            }
            // Any other native query without result class isn't expected in these tests.
            return mock(Query.class);
        });
    }

    @Test
    void testGetTodoDashboard_RoleRh_RetourneSectionsRh() {
        stubViews(
                Map.of("conges_a_valider", 3, "conges_urgents", 0, "salaires_mois_courant", 2),
                null,
                null
        );
        stubTableExists(Map.of(
                "missions", 0L,
                "frais_mission", 0L,
                "echeances_rh", 0L
        ));

        when(congeRepository.findByOrganisationIdAndStatutOrderByCreatedAtAsc(eq(orgId), eq(StatutConge.EN_ATTENTE), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var res = service.getTodoDashboard(orgId, "RH", userId);

        assertThat(res.sections()).hasSize(1);
        assertThat(res.sections().getFirst().roleLabel()).isEqualTo("Ressources Humaines");

        var groupes = res.sections().getFirst().groupes();
        var conges = groupes.stream().filter(g -> "CONGES_A_VALIDER".equals(g.categorie())).findFirst().orElseThrow();
        assertThat(conges.count()).isEqualTo(3);
        assertThat(groupes).noneMatch(g -> "SALAIRES_A_VERSER".equals(g.categorie()));
    }

    @Test
    void testGetTodoDashboard_RoleAdmin_RetourneTroisSections() {
        stubViews(
                Map.of("conges_a_valider", 1, "conges_urgents", 0, "salaires_mois_courant", 0),
                Map.of(
                        "factures_a_payer", 2,
                        "factures_en_retard", 1,
                        "montant_total_a_payer", BigDecimal.ZERO,
                        "salaires_a_verser", 3,
                        "montant_salaires_a_verser", BigDecimal.ZERO
                ),
                Map.of("factures_retard_critique", 1, "budgets_a_valider", 0, "biens_defaillants", 0)
        );
        stubTableExists(Map.of(
                "missions", 0L,
                "frais_mission", 0L,
                "echeances_rh", 0L,
                "titres_sejour", 0L,
                "formations_obligatoires", 0L
        ));

        when(congeRepository.findByOrganisationIdAndStatutOrderByCreatedAtAsc(eq(orgId), eq(StatutConge.EN_ATTENTE), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(factureRepository.findTop5ByOrganisationIdAndStatutOrderByDateFactureAsc(orgId, StatutFacture.A_PAYER))
                .thenReturn(List.of());
        when(paiementSalaireRepository.findTop5ByOrganisationIdAndStatutAndAnneeAndMoisOrderBySalarie_NomAscSalarie_PrenomAsc(
                any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        when(budgetAnnuelRepository.findByOrganisationIdAndAnneeAndStatut(any(), anyInt(), any()))
                .thenReturn(Optional.empty());
        when(factureRepository.findTop5ByOrganisationIdAndStatutAndDateFactureBeforeOrderByDateFactureAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(bienMaterielRepository.findTop5ByOrganisationIdAndEtatOrderByUpdatedAtDesc(any(), any()))
                .thenReturn(List.of());

        var res = service.getTodoDashboard(orgId, "ADMIN", userId);

        assertThat(res.sections()).hasSize(3);
        assertThat(res.sections().get(0).roleLabel()).isEqualTo("Ressources Humaines");
        assertThat(res.sections().get(1).roleLabel()).isEqualTo("Finance");
        assertThat(res.sections().get(2).roleLabel()).isEqualTo("Administration");

        assertThat(res.totalActionsGlobal()).isEqualTo(1 + (2 + 3) + 1);
    }

    @Test
    void testGetTodoDashboard_RoleEmploye_RetourneVide() {
        var res = service.getTodoDashboard(orgId, "EMPLOYE", userId);
        assertThat(res.sections()).isEmpty();
        assertThat(res.totalActionsGlobal()).isEqualTo(0);
    }

    @Test
    void testNiveauUrgence_CongeAncien_EstUrgent() {
        stubViews(
                Map.of("conges_a_valider", 1, "conges_urgents", 1, "salaires_mois_courant", 0),
                null,
                null
        );
        stubTableExists(Map.of("missions", 0L, "echeances_rh", 0L, "frais_mission", 0L));

        CongeAbsence c = new CongeAbsence();
        c.setId(UUID.fromString("c0000000-0000-0000-0000-000000000001"));
        c.setOrganisationId(orgId);
        Salarie s = new Salarie();
        s.setId(UUID.fromString("d0000000-0000-0000-0000-000000000001"));
        s.setNom("Dupont");
        s.setPrenom("Jean");
        s.setService("IT");
        c.setSalarie(s);
        c.setTypeConge(TypeConge.ANNUEL);
        c.setDateDebut(LocalDate.now().plusDays(1));
        c.setDateFin(LocalDate.now().plusDays(2));
        c.setNbJours(BigDecimal.valueOf(2));
        c.setStatut(StatutConge.EN_ATTENTE);
        c.setCreatedAt(Instant.now().minusSeconds(6L * 24 * 3600));

        when(congeRepository.findByOrganisationIdAndStatutOrderByCreatedAtAsc(eq(orgId), eq(StatutConge.EN_ATTENTE), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(c)));

        var section = service.buildSectionRh(orgId, userId);
        var groupe = section.groupes().stream().filter(g -> "CONGES_A_VALIDER".equals(g.categorie())).findFirst().orElseThrow();
        assertThat(groupe.items()).isNotEmpty();
        assertThat(groupe.items().getFirst().niveauUrgence()).isEqualTo("URGENT");
    }

    @Test
    void testNiveauUrgence_FactureEnRetard45j_EstCritique() {
        stubViews(
                Map.of("conges_a_valider", 0, "conges_urgents", 0, "salaires_mois_courant", 0),
                Map.of(
                        "factures_a_payer", 0,
                        "factures_en_retard", 0,
                        "montant_total_a_payer", BigDecimal.ZERO,
                        "salaires_a_verser", 0,
                        "montant_salaires_a_verser", BigDecimal.ZERO
                ),
                Map.of("factures_retard_critique", 1, "budgets_a_valider", 0, "biens_defaillants", 0)
        );
        stubTableExists(Map.of(
                "missions", 0L,
                "frais_mission", 0L,
                "echeances_rh", 0L,
                "titres_sejour", 0L,
                "formations_obligatoires", 0L
        ));

        when(congeRepository.findByOrganisationIdAndStatutOrderByCreatedAtAsc(eq(orgId), eq(StatutConge.EN_ATTENTE), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(factureRepository.findTop5ByOrganisationIdAndStatutOrderByDateFactureAsc(orgId, StatutFacture.A_PAYER))
                .thenReturn(List.of());
        when(paiementSalaireRepository.findTop5ByOrganisationIdAndStatutAndAnneeAndMoisOrderBySalarie_NomAscSalarie_PrenomAsc(
                any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(budgetAnnuelRepository.findByOrganisationIdAndAnneeAndStatut(any(), anyInt(), any()))
                .thenReturn(Optional.empty());
        when(bienMaterielRepository.findTop5ByOrganisationIdAndEtatOrderByUpdatedAtDesc(any(), any()))
                .thenReturn(List.of());

        Facture f = new Facture();
        f.setId(UUID.fromString("e0000000-0000-0000-0000-000000000001"));
        f.setOrganisationId(orgId);
        f.setReference("FAC-2026-0001");
        f.setFournisseur("ACME");
        f.setDateFacture(LocalDate.now().minusDays(46));
        f.setMontantTtc(BigDecimal.valueOf(750));
        f.setMontantHt(BigDecimal.valueOf(625));
        f.setDevise("EUR");
        f.setStatut(StatutFacture.A_PAYER);

        when(factureRepository.findTop5ByOrganisationIdAndStatutAndDateFactureBeforeOrderByDateFactureAsc(
                eq(orgId), eq(StatutFacture.A_PAYER), any(LocalDate.class)))
                .thenReturn(List.of(f));

        var res = service.getTodoDashboard(orgId, "ADMIN", userId);
        var adminSection = res.sections().stream().filter(s -> "Administration".equals(s.roleLabel())).findFirst().orElseThrow();
        var grp = adminSection.groupes().stream().filter(g -> "FACTURES_RETARD_CRITIQUE".equals(g.categorie())).findFirst().orElseThrow();
        assertThat(grp.count()).isEqualTo(1);
        assertThat(grp.items()).isNotEmpty();
        assertThat(grp.items().getFirst().niveauUrgence()).isEqualTo("CRITIQUE");
    }

    @Test
    void testExecuterAction_ValiderConge_AppelleCongeServiceExistant() {
        UUID congeId = UUID.fromString("f0000000-0000-0000-0000-000000000001");
        when(congeRepository.countByOrganisationIdAndStatut(orgId, StatutConge.EN_ATTENTE)).thenReturn(2L);

        var res = service.executerActionRapide(congeId, "CONGE", new QuickActionRequest("VALIDER_CONGE", null), orgId, userId);

        verify(congeService, times(1)).valider(congeId, userId, orgId);
        assertThat(res.succes()).isTrue();
    }

    @Test
    void testExecuterAction_RejeterConge_SansCommentaire_LanceException() {
        UUID congeId = UUID.fromString("f0000000-0000-0000-0000-000000000002");
        assertThatThrownBy(() ->
                service.executerActionRapide(congeId, "CONGE", new QuickActionRequest("REJETER_CONGE", ""), orgId, userId)
        ).isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("TODO_ACTION_IMPOSSIBLE");
    }

    @Test
    void testModulesOptionnels_TableMissionsAbsente_GroupeAbsent() {
        stubViews(
                Map.of("conges_a_valider", 0, "conges_urgents", 0, "salaires_mois_courant", 0),
                null,
                null
        );
        stubTableExists(Map.of("missions", 0L, "frais_mission", 0L, "echeances_rh", 0L));
        when(congeRepository.findByOrganisationIdAndStatutOrderByCreatedAtAsc(eq(orgId), eq(StatutConge.EN_ATTENTE), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var section = service.buildSectionRh(orgId, userId);
        assertThat(section.groupes()).noneMatch(g -> "MISSIONS_A_APPROUVER".equals(g.categorie()));
    }

    @Test
    void testHumanizeDelay_Exact() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(service.humanizeDelay(now.minusSeconds(30))).isEqualTo("il y a quelques secondes");
        assertThat(service.humanizeDelay(now.minusMinutes(90))).isEqualTo("il y a 1 heure");
        assertThat(service.humanizeDelay(now.minusDays(3))).isEqualTo("il y a 3 jours");
        assertThat(service.humanizeDelay(now.minusDays(15))).isEqualTo("il y a 2 semaines");
    }

    @Test
    void testGetTodoDashboard_RoleFinancier_RetourneSectionFinance() {
        stubViews(
                null,
                Map.of(
                        "factures_a_payer", 2,
                        "factures_en_retard", 0,
                        "montant_total_a_payer", BigDecimal.TEN,
                        "salaires_a_verser", 1,
                        "montant_salaires_a_verser", BigDecimal.ONE
                ),
                null
        );
        stubTableExists(Map.of("frais_mission", 0L));
        when(factureRepository.findTop5ByOrganisationIdAndStatutOrderByDateFactureAsc(orgId, StatutFacture.A_PAYER))
                .thenReturn(List.of());
        when(paiementSalaireRepository.findTop5ByOrganisationIdAndStatutAndAnneeAndMoisOrderBySalarie_NomAscSalarie_PrenomAsc(
                any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(budgetAnnuelRepository.findByOrganisationIdAndAnneeAndStatut(any(), anyInt(), any()))
                .thenReturn(Optional.empty());

        var res = service.getTodoDashboard(orgId, "FINANCIER", userId);

        assertThat(res.sections()).hasSize(1);
        assertThat(res.sections().getFirst().roleLabel()).isEqualTo("Finance");
        assertThat(res.totalActionsGlobal()).isEqualTo(2 + 1);
    }

    @Test
    void testGetComptages_Employe_Zero() {
        assertThat(service.getComptages(orgId, "EMPLOYE", userId)).containsEntry("total", 0L);
    }

    @Test
    void testGetComptages_Financier_AvecBudget() {
        stubViews(
                null,
                Map.of("factures_a_payer", 1L, "factures_en_retard", 0L, "salaires_a_verser", 2L),
                null
        );
        stubTableExists(Map.of("frais_mission", 0L));
        when(budgetAnnuelRepository.existsByOrganisationIdAndAnneeAndStatutIn(any(), anyInt(), any()))
                .thenReturn(true);

        Map<String, Long> counts = service.getComptages(orgId, "FINANCIER", userId);

        assertThat(counts.get("factures_a_payer")).isEqualTo(1L);
        assertThat(counts.get("salaires_a_verser")).isEqualTo(2L);
        assertThat(counts.get("budget_a_valider")).isEqualTo(1L);
        assertThat(counts.get("total")).isEqualTo(1L + 2L + 1L);
    }

    @Test
    void testExecuterAction_MarquerPaiePayee() {
        UUID paieId = UUID.fromString("f0000000-0000-0000-0000-000000000099");

        var res = service.executerActionRapide(
                paieId, "PAIE", new QuickActionRequest("MARQUER_PAIE_PAYEE", "OK"), orgId, userId);

        verify(paieService).marquerPaye(eq(paieId), any(), eq(orgId));
        assertThat(res.succes()).isTrue();
        assertThat(res.nouveauStatut()).isEqualTo("PAYE");
    }

    @Test
    void testExecuterAction_Inconnue_Refuse() {
        assertThatThrownBy(() ->
                        service.executerActionRapide(
                                UUID.randomUUID(), "X", new QuickActionRequest("INCONNUE", null), orgId, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TODO_ACTION_IMPOSSIBLE");
    }
}


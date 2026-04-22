package com.app.modules.budget.service;

import com.app.modules.budget.dto.BudgetRequest;
import com.app.modules.budget.dto.LigneBudgetRequest;
import com.app.modules.budget.dto.ModifierLigneBudgetRequest;
import com.app.modules.budget.entity.BudgetAnnuel;
import com.app.modules.budget.entity.LigneBudget;
import com.app.modules.budget.entity.StatutBudget;
import com.app.modules.finance.entity.CategorieDepense;
import com.app.modules.finance.entity.TypeCategorie;
import com.app.modules.budget.repository.BudgetAnnuelRepository;
import com.app.modules.budget.repository.LigneBudgetRepository;
import com.app.modules.finance.repository.CategorieDepenseRepository;
import com.app.audit.AuditLogService;
import com.app.modules.auth.repository.OrganisationRepository;
import com.app.modules.auth.repository.UtilisateurRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock private BudgetAnnuelRepository budgetAnnuelRepository;
    @Mock private LigneBudgetRepository ligneBudgetRepository;
    @Mock private CategorieDepenseRepository categorieDepenseRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private BudgetService budgetService;

    private final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @Test
    void testCreerBudget_AnneeExistante_LanceException() {
        when(budgetAnnuelRepository.existsByOrganisationIdAndAnneeAndStatutIn(
                        eq(orgId), eq(2025), any()))
                .thenReturn(true);

        BudgetRequest req =
                new BudgetRequest(
                        2025,
                        List.of(new LigneBudgetRequest(UUID.randomUUID(), "DEPENSE", new BigDecimal("1000"))),
                        "notes");

        assertThatThrownBy(() -> budgetService.creer(req, orgId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "BUDGET_BROUILLON_EXISTE");
    }

    @Test
    void testCreerBudget_LigneTypeCategorieMismatch_Refuse() {
        when(budgetAnnuelRepository.existsByOrganisationIdAndAnneeAndStatutIn(eq(orgId), eq(2025), any())).thenReturn(false);
        when(budgetAnnuelRepository.save(any(BudgetAnnuel.class))).thenAnswer(inv -> {
            BudgetAnnuel b = inv.getArgument(0);
            if (b.getId() == null) b.setId(UUID.fromString("c0000000-0000-0000-0000-000000000001"));
            return b;
        });

        UUID catId = UUID.fromString("d0000000-0000-0000-0000-000000000001");
        CategorieDepense cat = new CategorieDepense();
        cat.setId(catId);
        cat.setOrganisationId(orgId);
        cat.setType(TypeCategorie.RECETTE); // mismatch vs DEPENSE in request
        when(categorieDepenseRepository.findByIdAndOrganisationId(catId, orgId)).thenReturn(Optional.of(cat));

        BudgetRequest req =
                new BudgetRequest(
                        2025,
                        List.of(new LigneBudgetRequest(catId, "DEPENSE", new BigDecimal("1000"))),
                        "notes");

        assertThatThrownBy(() -> budgetService.creer(req, orgId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "BUDGET_LIGNE_TYPE_CATEGORIE");
    }

    @Test
    void testModifierLigne_BudgetCloture_Refuse() {
        UUID budgetId = UUID.fromString("e0000000-0000-0000-0000-000000000001");
        UUID ligneId = UUID.fromString("e1000000-0000-0000-0000-000000000001");

        BudgetAnnuel b = new BudgetAnnuel();
        b.setId(budgetId);
        b.setOrganisationId(orgId);
        b.setStatut(StatutBudget.CLOTURE);
        when(budgetAnnuelRepository.findById(budgetId)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> budgetService.modifierLigne(
                budgetId, ligneId, new ModifierLigneBudgetRequest(new BigDecimal("10.00")), orgId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "BUDGET_CLOTURE");
        verify(ligneBudgetRepository, never()).save(any(LigneBudget.class));
    }

    @Test
    void testValider_SiPasBrouillon_Refuse() {
        UUID budgetId = UUID.fromString("f0000000-0000-0000-0000-000000000001");
        BudgetAnnuel b = new BudgetAnnuel();
        b.setId(budgetId);
        b.setOrganisationId(orgId);
        b.setAnnee(2025);
        b.setStatut(StatutBudget.VALIDE);
        b.setDateValidation(Instant.now());
        when(budgetAnnuelRepository.findById(budgetId)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> budgetService.valider(budgetId, orgId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "BUDGET_DEJA_VALIDE");
    }

    @Test
    void testGetBudget_Absent_Refuse() {
        when(budgetAnnuelRepository.findByOrganisationIdAndAnneeAndStatut(orgId, 2025, StatutBudget.VALIDE)).thenReturn(Optional.empty());
        when(budgetAnnuelRepository.findByOrganisationIdAndAnneeAndStatut(orgId, 2025, StatutBudget.BROUILLON)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.getBudget(orgId, 2025))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "BUDGET_ABSENT");
    }

    @Test
    void testToResponse_OrganisationAbsente_Refuse() {
        UUID budgetId = UUID.fromString("f1000000-0000-0000-0000-000000000001");
        BudgetAnnuel b = new BudgetAnnuel();
        b.setId(budgetId);
        b.setOrganisationId(orgId);
        b.setAnnee(2025);
        b.setStatut(StatutBudget.BROUILLON);
        when(budgetAnnuelRepository.findByOrganisationIdAndAnneeAndStatut(orgId, 2025, StatutBudget.VALIDE)).thenReturn(Optional.empty());
        when(budgetAnnuelRepository.findByOrganisationIdAndAnneeAndStatut(orgId, 2025, StatutBudget.BROUILLON)).thenReturn(Optional.of(b));
        when(organisationRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.getBudget(orgId, 2025))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ORG_ABSENTE");
    }
}

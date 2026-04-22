package com.app.modules.budget.service;

import com.app.modules.budget.dto.BudgetRequest;
import com.app.modules.budget.dto.LigneBudgetRequest;
import com.app.modules.budget.entity.StatutBudget;
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
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
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
                        eq(orgId), eq(2025), eq(EnumSet.of(StatutBudget.VALIDE, StatutBudget.CLOTURE))))
                .thenReturn(true);

        BudgetRequest req =
                new BudgetRequest(
                        2025,
                        List.of(new LigneBudgetRequest(UUID.randomUUID(), "DEPENSE", new BigDecimal("1000"))),
                        "notes");

        assertThatThrownBy(() -> budgetService.creer(req, orgId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "BUDGET_ANNEE_EXISTE");
    }
}

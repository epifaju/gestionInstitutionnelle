package com.app.modules.payroll.service;

import com.app.audit.AuditLogService;
import com.app.modules.payroll.dto.PayrollEmployerSettingsRequest;
import com.app.modules.payroll.entity.PayrollEmployerSettings;
import com.app.modules.payroll.entity.PayrollLegalConstant;
import com.app.modules.payroll.repository.EmployeePayrollProfileRepository;
import com.app.modules.payroll.repository.PayrollCotisationRepository;
import com.app.modules.payroll.repository.PayrollEmployerSettingsRepository;
import com.app.modules.payroll.repository.PayrollLegalConstantRepository;
import com.app.modules.payroll.repository.PayrollRubriqueRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import com.app.modules.payroll.entity.PayrollRubrique;

@ExtendWith(MockitoExtension.class)
class PayrollAdminServiceTest {

    @Mock PayrollEmployerSettingsRepository employerSettingsRepository;
    @Mock PayrollLegalConstantRepository legalConstantRepository;
    @Mock PayrollRubriqueRepository rubriqueRepository;
    @Mock PayrollCotisationRepository cotisationRepository;
    @Mock EmployeePayrollProfileRepository profileRepository;
    @Mock SalarieRepository salarieRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks PayrollAdminService service;

    @Test
    void upsertEmployerSettings_createsWhenMissing() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(employerSettingsRepository.findById(orgId)).thenReturn(Optional.empty());

        PayrollEmployerSettingsRequest req = new PayrollEmployerSettingsRequest(
                "ACME",
                "L1",
                null,
                "75000",
                "Paris",
                "FR",
                "SIRET",
                "NAF",
                "URSSAF",
                "CC",
                "Convention"
        );

        var out = service.upsertEmployerSettings(orgId, userId, req);
        assertThat(out.raisonSociale()).isEqualTo("ACME");
        verify(employerSettingsRepository).save(any(PayrollEmployerSettings.class));
        verify(auditLogService).log(eq(orgId), eq(userId), eq("UPDATE"), eq("PayrollEmployerSettings"), eq(orgId), eq(null), eq(req));
    }

    @Test
    void deleteLegalConstant_refusesOtherOrg() {
        UUID orgId = UUID.randomUUID();
        UUID otherOrg = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        PayrollLegalConstant c = new PayrollLegalConstant();
        c.setId(id);
        c.setOrganisationId(otherOrg);
        c.setCode("PMSS");
        c.setLibelle("x");
        c.setValeur(BigDecimal.ONE);
        c.setEffectiveFrom(LocalDate.of(2026, 1, 1));

        when(legalConstantRepository.findById(id)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.deleteLegalConstant(id, orgId, userId))
                .isInstanceOf(BusinessException.class);

        verify(legalConstantRepository, never()).delete(any());
    }

    @Test
    void listRubriques_filtersByOrganisation() {
        UUID orgId = UUID.randomUUID();
        UUID otherOrg = UUID.randomUUID();

        PayrollRubrique r1 = new PayrollRubrique();
        r1.setOrganisationId(orgId);
        r1.setCode("R1");
        r1.setLibelle("A");

        PayrollRubrique r2 = new PayrollRubrique();
        r2.setOrganisationId(otherOrg);
        r2.setCode("R2");
        r2.setLibelle("B");

        when(rubriqueRepository.findAll()).thenReturn(List.of(r1, r2));

        var out = service.listRubriques(orgId);
        assertThat(out).hasSize(1);
        assertThat(out.getFirst().code()).isEqualTo("R1");
    }
}


package com.app.modules.payroll.service;

import com.app.audit.AuditLogService;
import com.app.modules.payroll.repository.BulletinLigneRepository;
import com.app.modules.payroll.repository.BulletinPaieRepository;
import com.app.modules.payroll.repository.EmployeePayrollProfileRepository;
import com.app.modules.payroll.repository.PayrollCotisationRepository;
import com.app.modules.payroll.repository.PayrollEmployerSettingsRepository;
import com.app.modules.payroll.repository.PayrollLegalConstantRepository;
import com.app.modules.payroll.repository.PayrollRubriqueRepository;
import com.app.modules.rh.entity.PaiementSalaire;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.repository.HistoriqueSalaireRepository;
import com.app.modules.rh.repository.PaiementSalaireRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulletinPaieServiceTest {

    @Mock BulletinPaieRepository bulletinPaieRepository;
    @Mock BulletinLigneRepository bulletinLigneRepository;
    @Mock PayrollEmployerSettingsRepository employerSettingsRepository;
    @Mock EmployeePayrollProfileRepository employeePayrollProfileRepository;
    @Mock PayrollLegalConstantRepository payrollLegalConstantRepository;
    @Mock PayrollRubriqueRepository payrollRubriqueRepository;
    @Mock PayrollCotisationRepository payrollCotisationRepository;
    @Mock HistoriqueSalaireRepository historiqueSalaireRepository;
    @Mock PaiementSalaireRepository paiementSalaireRepository;
    @Mock MinioStorageService minioStorageService;
    @Mock AuditLogService auditLogService;

    @InjectMocks BulletinPaieService service;

    @Test
    void generateForPaiementSalaire_throwsWhenPaiementAbsent() {
        UUID paiementId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(paiementSalaireRepository.findById(paiementId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateForPaiementSalaire(paiementId, orgId, userId))
                .isInstanceOf(BusinessException.class);

        verify(bulletinPaieRepository, never()).save(any());
    }

    @Test
    void generateForPaiementSalaire_throwsWhenOrgMismatch() {
        UUID paiementId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID otherOrg = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PaiementSalaire p = new PaiementSalaire();
        p.setId(paiementId);
        p.setOrganisationId(otherOrg);
        p.setDatePaiement(LocalDate.of(2026, 4, 24));

        when(paiementSalaireRepository.findById(paiementId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.generateForPaiementSalaire(paiementId, orgId, userId))
                .isInstanceOf(BusinessException.class);

        verify(bulletinPaieRepository, never()).save(any());
    }

    @Test
    void generateForPaiementSalaire_throwsWhenNoDatePaiement() {
        UUID paiementId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PaiementSalaire p = new PaiementSalaire();
        p.setId(paiementId);
        p.setOrganisationId(orgId);
        p.setDatePaiement(null);

        when(paiementSalaireRepository.findById(paiementId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.generateForPaiementSalaire(paiementId, orgId, userId))
                .isInstanceOf(BusinessException.class);

        verify(bulletinPaieRepository, never()).save(any());
    }

    @Test
    void generateForPaiementSalaire_throwsWhenBulletinAlreadyLinkedOnPaiement() {
        UUID paiementId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PaiementSalaire p = new PaiementSalaire();
        p.setId(paiementId);
        p.setOrganisationId(orgId);
        p.setDatePaiement(LocalDate.of(2026, 4, 24));
        p.setBulletinId(UUID.randomUUID());

        when(paiementSalaireRepository.findById(paiementId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.generateForPaiementSalaire(paiementId, orgId, userId))
                .isInstanceOf(BusinessException.class);

        verify(bulletinPaieRepository, never()).save(any());
    }

    @Test
    void generateForPaiementSalaire_throwsWhenDuplicateBulletinExists() {
        UUID paiementId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Salarie s = new Salarie();
        s.setId(UUID.randomUUID());
        s.setOrganisationId(orgId);

        PaiementSalaire p = new PaiementSalaire();
        p.setId(paiementId);
        p.setOrganisationId(orgId);
        p.setSalarie(s);
        p.setAnnee(2026);
        p.setMois(4);
        p.setDatePaiement(LocalDate.of(2026, 4, 24));
        p.setBulletinId(null);

        when(paiementSalaireRepository.findById(paiementId)).thenReturn(Optional.of(p));
        when(bulletinPaieRepository.findByOrganisationIdAndSalarie_IdAndAnneeAndMois(orgId, s.getId(), 2026, 4))
                .thenReturn(Optional.of(new com.app.modules.payroll.entity.BulletinPaie()));

        assertThatThrownBy(() -> service.generateForPaiementSalaire(paiementId, orgId, userId))
                .isInstanceOf(BusinessException.class);

        verify(bulletinPaieRepository, never()).save(any());
    }
}


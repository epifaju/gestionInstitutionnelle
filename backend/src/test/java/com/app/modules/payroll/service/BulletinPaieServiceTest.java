package com.app.modules.payroll.service;

import com.app.audit.AuditLogService;
import com.app.modules.payroll.entity.BulletinPaie;
import com.app.modules.payroll.entity.EmployeePayrollProfile;
import com.app.modules.payroll.repository.BulletinLigneRepository;
import com.app.modules.payroll.repository.BulletinPaieRepository;
import com.app.modules.payroll.repository.EmployeePayrollProfileRepository;
import com.app.modules.payroll.repository.PayrollCotisationRepository;
import com.app.modules.payroll.repository.PayrollEmployerSettingsRepository;
import com.app.modules.payroll.repository.PayrollLegalConstantRepository;
import com.app.modules.payroll.repository.PayrollRubriqueRepository;
import com.app.modules.rh.entity.HistoriqueSalaire;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID PAIEMENT_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID SALARIE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID BULLETIN_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");

    @Test
    void generateForPaiementSalaire_throwsWhenPaiementAbsent() {
        when(paiementSalaireRepository.findById(PAIEMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateForPaiementSalaire(PAIEMENT_ID, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class);

        verify(bulletinPaieRepository, never()).save(any());
    }

    @Test
    void generateForPaiementSalaire_throwsWhenOrgMismatch() {
        PaiementSalaire p = basePaiement();
        p.setOrganisationId(UUID.randomUUID());
        when(paiementSalaireRepository.findById(PAIEMENT_ID)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.generateForPaiementSalaire(PAIEMENT_ID, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class);

        verify(bulletinPaieRepository, never()).save(any());
    }

    @Test
    void generateForPaiementSalaire_throwsWhenNoDatePaiement() {
        PaiementSalaire p = basePaiement();
        p.setDatePaiement(null);
        when(paiementSalaireRepository.findById(PAIEMENT_ID)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.generateForPaiementSalaire(PAIEMENT_ID, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class);

        verify(bulletinPaieRepository, never()).save(any());
    }

    @Test
    void generateForPaiementSalaire_throwsWhenBulletinAlreadyLinkedOnPaiement() {
        PaiementSalaire p = basePaiement();
        p.setBulletinId(UUID.randomUUID());
        when(paiementSalaireRepository.findById(PAIEMENT_ID)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.generateForPaiementSalaire(PAIEMENT_ID, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class);

        verify(bulletinPaieRepository, never()).save(any());
    }

    @Test
    void generateForPaiementSalaire_throwsWhenDuplicateBulletinExists() {
        PaiementSalaire p = basePaiement();
        when(paiementSalaireRepository.findById(PAIEMENT_ID)).thenReturn(Optional.of(p));
        when(bulletinPaieRepository.findByOrganisationIdAndSalarie_IdAndAnneeAndMois(ORG_ID, SALARIE_ID, 2026, 4))
                .thenReturn(Optional.of(new BulletinPaie()));

        assertThatThrownBy(() -> service.generateForPaiementSalaire(PAIEMENT_ID, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class);

        verify(bulletinPaieRepository, never()).save(any());
    }

    @Test
    void generateForPaiementSalaire_throwsWhenGrilleAbsenteEtSansMontant() {
        PaiementSalaire p = basePaiement();
        p.setMontant(null);
        when(paiementSalaireRepository.findById(PAIEMENT_ID)).thenReturn(Optional.of(p));
        when(bulletinPaieRepository.findByOrganisationIdAndSalarie_IdAndAnneeAndMois(ORG_ID, SALARIE_ID, 2026, 4))
                .thenReturn(Optional.empty());
        when(employeePayrollProfileRepository.findByOrganisationIdAndSalarie_Id(ORG_ID, SALARIE_ID))
                .thenReturn(Optional.empty());
        when(historiqueSalaireRepository.findBySalarie_IdOrderByDateDebutDesc(SALARIE_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.generateForPaiementSalaire(PAIEMENT_ID, ORG_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "GRILLE_SALAIRE_ABSENTE");
    }

    @Test
    void generateForPaiementSalaire_ok_avecGrilleEtPdf() throws Exception {
        PaiementSalaire p = basePaiement();
        when(paiementSalaireRepository.findById(PAIEMENT_ID)).thenReturn(Optional.of(p));
        when(bulletinPaieRepository.findByOrganisationIdAndSalarie_IdAndAnneeAndMois(ORG_ID, SALARIE_ID, 2026, 4))
                .thenReturn(Optional.empty());

        EmployeePayrollProfile profile = new EmployeePayrollProfile();
        profile.setCadre(false);
        profile.setConventionCode("CCN");
        profile.setConventionLibelle("Convention");
        when(employeePayrollProfileRepository.findByOrganisationIdAndSalarie_Id(ORG_ID, SALARIE_ID))
                .thenReturn(Optional.of(profile));

        HistoriqueSalaire grille = new HistoriqueSalaire();
        grille.setMontantBrut(new BigDecimal("3000.00"));
        grille.setDateDebut(LocalDate.of(2026, 1, 1));
        when(historiqueSalaireRepository.findBySalarie_IdOrderByDateDebutDesc(SALARIE_ID)).thenReturn(List.of(grille));

        LocalDate datePaiement = LocalDate.of(2026, 4, 24);
        when(payrollRubriqueRepository.listEffective(ORG_ID, datePaiement)).thenReturn(List.of());
        when(payrollCotisationRepository.listEffective(ORG_ID, datePaiement)).thenReturn(List.of());
        when(payrollLegalConstantRepository.findEffective(eq(ORG_ID), eq("PMSS"), eq(datePaiement)))
                .thenReturn(Optional.empty());
        when(employerSettingsRepository.findById(ORG_ID)).thenReturn(Optional.empty());
        when(bulletinPaieRepository.save(any(BulletinPaie.class))).thenAnswer(inv -> {
            BulletinPaie b = inv.getArgument(0);
            if (b.getId() == null) b.setId(BULLETIN_ID);
            return b;
        });
        when(bulletinLigneRepository.findByBulletin_IdOrderByOrdreAffichageAsc(BULLETIN_ID)).thenReturn(List.of());

        BulletinPaie out = service.generateForPaiementSalaire(PAIEMENT_ID, ORG_ID, USER_ID);

        assertThat(out.getId()).isEqualTo(BULLETIN_ID);
        assertThat(out.getBrut()).isEqualByComparingTo("3000.00");
        assertThat(out.getPdfObjectName()).contains("payslips/" + ORG_ID + "/" + SALARIE_ID);
        assertThat(p.getBulletinId()).isEqualTo(BULLETIN_ID);
        verify(minioStorageService).upload(anyString(), any(), anyLong(), eq("application/pdf"));
        verify(paiementSalaireRepository).save(p);
        verify(auditLogService).logRh(eq(ORG_ID), eq(USER_ID), eq("CREATE"), eq("BulletinPaie"), eq(BULLETIN_ID), eq(null), any());
    }

    @Test
    void generateForPaiementSalaire_ok_fallbackMontantPaiement() throws Exception {
        PaiementSalaire p = basePaiement();
        p.setMontant(new BigDecimal("2500.00"));
        when(paiementSalaireRepository.findById(PAIEMENT_ID)).thenReturn(Optional.of(p));
        when(bulletinPaieRepository.findByOrganisationIdAndSalarie_IdAndAnneeAndMois(ORG_ID, SALARIE_ID, 2026, 4))
                .thenReturn(Optional.empty());
        when(employeePayrollProfileRepository.findByOrganisationIdAndSalarie_Id(ORG_ID, SALARIE_ID))
                .thenReturn(Optional.empty());
        when(historiqueSalaireRepository.findBySalarie_IdOrderByDateDebutDesc(SALARIE_ID)).thenReturn(List.of());
        LocalDate datePaiement = LocalDate.of(2026, 4, 24);
        when(payrollRubriqueRepository.listEffective(ORG_ID, datePaiement)).thenReturn(List.of());
        when(payrollCotisationRepository.listEffective(ORG_ID, datePaiement)).thenReturn(List.of());
        when(employerSettingsRepository.findById(ORG_ID)).thenReturn(Optional.empty());
        when(bulletinPaieRepository.save(any(BulletinPaie.class))).thenAnswer(inv -> {
            BulletinPaie b = inv.getArgument(0);
            if (b.getId() == null) b.setId(BULLETIN_ID);
            return b;
        });
        when(bulletinLigneRepository.findByBulletin_IdOrderByOrdreAffichageAsc(BULLETIN_ID)).thenReturn(List.of());

        BulletinPaie out = service.generateForPaiementSalaire(PAIEMENT_ID, ORG_ID, USER_ID);
        assertThat(out.getBrut()).isEqualByComparingTo("2500.00");
    }

    @Test
    void downloadMyBulletinForSalarie_ok() throws Exception {
        BulletinPaie b = new BulletinPaie();
        b.setPdfObjectName("payslips/x.pdf");
        when(bulletinPaieRepository.findByOrganisationIdAndSalarie_IdAndAnneeAndMois(ORG_ID, SALARIE_ID, 2026, 4))
                .thenReturn(Optional.of(b));
        when(minioStorageService.download("payslips/x.pdf"))
                .thenReturn(new MinioStorageService.Download(
                        "payslips/x.pdf", "application/pdf", 3, new ByteArrayInputStream(new byte[]{1, 2, 3})));

        var dl = service.downloadMyBulletinForSalarie(ORG_ID, SALARIE_ID, 2026, 4);
        assertThat(dl.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void downloadMyBulletinForSalarie_refusePdfAbsent() {
        BulletinPaie b = new BulletinPaie();
        b.setPdfObjectName(" ");
        when(bulletinPaieRepository.findByOrganisationIdAndSalarie_IdAndAnneeAndMois(ORG_ID, SALARIE_ID, 2026, 4))
                .thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.downloadMyBulletinForSalarie(ORG_ID, SALARIE_ID, 2026, 4))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "BULLETIN_PDF_NOT_FOUND");
    }

    @Test
    void presignedUrlForSalarie_ok() throws Exception {
        BulletinPaie b = new BulletinPaie();
        b.setPdfObjectName("payslips/x.pdf");
        when(bulletinPaieRepository.findByOrganisationIdAndSalarie_IdAndAnneeAndMois(ORG_ID, SALARIE_ID, 2026, 3))
                .thenReturn(Optional.of(b));
        when(minioStorageService.presignedGetUrl("payslips/x.pdf")).thenReturn("https://minio/x.pdf");

        assertThat(service.presignedUrlForSalarie(ORG_ID, SALARIE_ID, 2026, 3)).isEqualTo("https://minio/x.pdf");
    }

    @Test
    void presignedUrlForSalarie_notFound() {
        when(bulletinPaieRepository.findByOrganisationIdAndSalarie_IdAndAnneeAndMois(ORG_ID, SALARIE_ID, 2026, 3))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.presignedUrlForSalarie(ORG_ID, SALARIE_ID, 2026, 3))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "BULLETIN_NOT_FOUND");
    }

    private PaiementSalaire basePaiement() {
        Salarie s = new Salarie();
        s.setId(SALARIE_ID);
        s.setOrganisationId(ORG_ID);
        s.setNom("Dupont");
        s.setPrenom("Alice");
        s.setMatricule("M001");

        PaiementSalaire p = new PaiementSalaire();
        p.setId(PAIEMENT_ID);
        p.setOrganisationId(ORG_ID);
        p.setSalarie(s);
        p.setAnnee(2026);
        p.setMois(4);
        p.setDatePaiement(LocalDate.of(2026, 4, 24));
        p.setMontant(new BigDecimal("2800.00"));
        p.setDevise("EUR");
        p.setBulletinId(null);
        return p;
    }
}

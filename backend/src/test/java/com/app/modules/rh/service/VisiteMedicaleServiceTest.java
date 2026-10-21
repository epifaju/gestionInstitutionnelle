package com.app.modules.rh.service;

import com.app.modules.rh.dto.VisiteMedicaleRequest;
import com.app.modules.rh.entity.EcheanceRh;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.entity.StatutEcheance;
import com.app.modules.rh.entity.StatutVisite;
import com.app.modules.rh.entity.VisiteMedicale;
import com.app.modules.rh.repository.EcheanceRhRepository;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.modules.rh.repository.VisiteMedicaleRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

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
class VisiteMedicaleServiceTest {

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SALARIE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID VISITE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID ECHEANCE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");

    @Mock private VisiteMedicaleRepository visiteRepository;
    @Mock private SalarieRepository salarieRepository;
    @Mock private EcheanceRhRepository echeanceRepository;
    @Mock private MinioStorageService minioStorageService;

    @InjectMocks private VisiteMedicaleService visiteMedicaleService;

    private Salarie salarie() {
        Salarie s = new Salarie();
        s.setId(SALARIE_ID);
        s.setOrganisationId(ORG_ID);
        s.setNom("Dupont");
        s.setPrenom("Alice");
        return s;
    }

    @Test
    void creerVisite_refuseSiDateRealiseeAvantPlanifiee() {
        when(salarieRepository.findByIdAndOrganisationId(SALARIE_ID, ORG_ID)).thenReturn(Optional.of(salarie()));
        VisiteMedicaleRequest req = new VisiteMedicaleRequest(
                "Embauche",
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 4, 1),
                null,
                null,
                "OK",
                null,
                24);

        assertThatThrownBy(() -> visiteMedicaleService.creerVisite(SALARIE_ID, req, ORG_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ECHEANCE_DATE_INVALIDE");
        verify(visiteRepository, never()).save(any());
    }

    @Test
    void creerVisite_refuseSalarieAbsent() {
        when(salarieRepository.findByIdAndOrganisationId(SALARIE_ID, ORG_ID)).thenReturn(Optional.empty());
        VisiteMedicaleRequest req = new VisiteMedicaleRequest(
                "Embauche", LocalDate.of(2026, 5, 1), null, null, null, null, null, 24);

        assertThatThrownBy(() -> visiteMedicaleService.creerVisite(SALARIE_ID, req, ORG_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SALARIE_NON_TROUVE");
    }

    @Test
    void creerVisite_ok_avecEcheanceSuivi() {
        when(salarieRepository.findByIdAndOrganisationId(SALARIE_ID, ORG_ID)).thenReturn(Optional.of(salarie()));
        when(echeanceRepository.save(any(EcheanceRh.class))).thenAnswer(inv -> {
            EcheanceRh e = inv.getArgument(0);
            e.setId(ECHEANCE_ID);
            return e;
        });
        when(visiteRepository.save(any(VisiteMedicale.class))).thenAnswer(inv -> {
            VisiteMedicale v = inv.getArgument(0);
            v.setId(VISITE_ID);
            return v;
        });

        LocalDate realisee = LocalDate.now().plusMonths(2);
        VisiteMedicaleRequest req = new VisiteMedicaleRequest(
                "Periodique",
                LocalDate.now().plusMonths(1),
                realisee,
                "Dr House",
                "Centre A",
                "APTE",
                null,
                24);

        var out = visiteMedicaleService.creerVisite(SALARIE_ID, req, ORG_ID);
        assertThat(out.id()).isEqualTo(VISITE_ID);
        assertThat(out.prochaineVisite()).isEqualTo(realisee.plusMonths(24));
        assertThat(out.salarieNomComplet()).contains("Dupont");
        verify(echeanceRepository).save(any(EcheanceRh.class));
    }

    @Test
    void creerVisite_sansRealisation_pasDEcheance() {
        when(salarieRepository.findByIdAndOrganisationId(SALARIE_ID, ORG_ID)).thenReturn(Optional.of(salarie()));
        when(visiteRepository.save(any(VisiteMedicale.class))).thenAnswer(inv -> {
            VisiteMedicale v = inv.getArgument(0);
            v.setId(VISITE_ID);
            return v;
        });

        VisiteMedicaleRequest req = new VisiteMedicaleRequest(
                "Embauche", LocalDate.now().plusDays(10), null, null, null, null, null, null);

        var out = visiteMedicaleService.creerVisite(SALARIE_ID, req, ORG_ID);
        assertThat(out.prochaineVisite()).isNull();
        assertThat(out.periodiciteMois()).isEqualTo(24);
        verify(echeanceRepository, never()).save(any());
    }

    @Test
    void enregistrerResultat_ok_marqueEcheanceTraitee() throws Exception {
        VisiteMedicale v = new VisiteMedicale();
        v.setId(VISITE_ID);
        v.setOrganisationId(ORG_ID);
        v.setSalarie(salarie());
        v.setEcheanceId(ECHEANCE_ID);
        when(visiteRepository.findById(VISITE_ID)).thenReturn(Optional.of(v));
        when(visiteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EcheanceRh e = new EcheanceRh();
        e.setId(ECHEANCE_ID);
        e.setStatut(StatutEcheance.A_VENIR);
        when(echeanceRepository.findByIdAndOrganisationId(ECHEANCE_ID, ORG_ID)).thenReturn(Optional.of(e));

        var out = visiteMedicaleService.enregistrerResultat(VISITE_ID, "APTE", "aucune", null, ORG_ID);
        assertThat(out.statut()).isEqualTo(StatutVisite.REALISEE.name());
        assertThat(out.resultat()).isEqualTo("APTE");
        assertThat(e.getStatut()).isEqualTo(StatutEcheance.TRAITEE);
        verify(minioStorageService, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void enregistrerResultat_refuseFichierTropGrand() {
        VisiteMedicale v = new VisiteMedicale();
        v.setId(VISITE_ID);
        v.setOrganisationId(ORG_ID);
        v.setSalarie(salarie());
        when(visiteRepository.findById(VISITE_ID)).thenReturn(Optional.of(v));

        MockMultipartFile file = new MockMultipartFile(
                "file", "cr.pdf", "application/pdf", new byte[10_485_761]);
        assertThatThrownBy(() -> visiteMedicaleService.enregistrerResultat(VISITE_ID, "OK", null, file, ORG_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FICHIER_TROP_GRAND");
    }

    @Test
    void enregistrerResultat_refuseOrgMismatch() {
        VisiteMedicale v = new VisiteMedicale();
        v.setId(VISITE_ID);
        v.setOrganisationId(UUID.randomUUID());
        when(visiteRepository.findById(VISITE_ID)).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> visiteMedicaleService.enregistrerResultat(VISITE_ID, "OK", null, null, ORG_ID))
                .isInstanceOf(BusinessException.class);
        verify(visiteRepository, never()).save(any());
    }

    @Test
    void enregistrerResultat_refuseNonPdf() {
        VisiteMedicale v = new VisiteMedicale();
        v.setId(VISITE_ID);
        v.setOrganisationId(ORG_ID);
        v.setSalarie(salarie());
        when(visiteRepository.findById(VISITE_ID)).thenReturn(Optional.of(v));

        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> visiteMedicaleService.enregistrerResultat(VISITE_ID, "OK", null, file, ORG_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "UPLOAD_ECHEC");
    }

    @Test
    void getVisitesSalarie_ok() {
        when(salarieRepository.findByIdAndOrganisationId(SALARIE_ID, ORG_ID)).thenReturn(Optional.of(salarie()));
        VisiteMedicale v = new VisiteMedicale();
        v.setId(VISITE_ID);
        v.setOrganisationId(ORG_ID);
        v.setSalarie(salarie());
        v.setTypeVisite("Embauche");
        v.setCompteRenduUrl("https://cdn/cr.pdf");
        when(visiteRepository.findBySalarieIdOrderByDateRealiseeDesc(SALARIE_ID)).thenReturn(List.of(v));

        var out = visiteMedicaleService.getVisitesSalarie(SALARIE_ID, ORG_ID);
        assertThat(out).hasSize(1);
        assertThat(out.getFirst().compteRenduUrl()).isEqualTo("https://cdn/cr.pdf");
    }
}

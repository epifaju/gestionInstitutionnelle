package com.app.modules.missions.service;

import com.app.modules.finance.service.FactureService;
import com.app.modules.finance.service.TauxChangeService;
import com.app.modules.missions.dto.FraisRequest;
import com.app.modules.missions.dto.FraisResponse;
import com.app.modules.missions.dto.MissionRequest;
import com.app.modules.missions.dto.MissionResponse;
import com.app.modules.missions.entity.FraisMission;
import com.app.modules.missions.entity.Mission;
import com.app.modules.missions.entity.StatutFrais;
import com.app.modules.missions.entity.StatutMission;
import com.app.modules.missions.repository.FraisMissionRepository;
import com.app.modules.missions.repository.MissionRepository;
import com.app.modules.rh.entity.Salarie;
import com.app.modules.rh.repository.SalarieRepository;
import com.app.shared.exception.BusinessException;
import com.app.shared.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID SALARIE_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID MISSION_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID FRAIS_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");

    @Mock MissionRepository missionRepository;
    @Mock FraisMissionRepository fraisMissionRepository;
    @Mock SalarieRepository salarieRepository;
    @Mock MinioStorageService minioStorageService;
    @Mock TauxChangeService tauxChangeService;
    @Mock FactureService factureService;

    @InjectMocks MissionService service;

    private Salarie salarie() {
        Salarie s = new Salarie();
        s.setId(SALARIE_ID);
        s.setOrganisationId(ORG_ID);
        s.setNom("Doe");
        s.setPrenom("Jane");
        return s;
    }

    private Mission mission(StatutMission statut) {
        Mission m = new Mission();
        m.setId(MISSION_ID);
        m.setOrganisationId(ORG_ID);
        m.setSalarie(salarie());
        m.setStatut(statut);
        m.setTitre("Mission X");
        m.setDestination("Paris");
        m.setPaysDestination("FR");
        m.setObjectif("Audit");
        m.setDateDepart(LocalDate.of(2026, 4, 24));
        m.setDateRetour(LocalDate.of(2026, 4, 26));
        m.setAvanceDevise("EUR");
        m.setCreatedAt(Instant.parse("2026-01-10T10:00:00Z"));
        return m;
    }

    private MissionRequest req() {
        return new MissionRequest(
                "Mission X",
                "Paris",
                "FR",
                "Audit",
                LocalDate.of(2026, 4, 24),
                LocalDate.of(2026, 4, 26),
                new BigDecimal("100.00"),
                "EUR");
    }

    private void stubGetById(Mission m) {
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));
        when(fraisMissionRepository.findByMission_IdOrderByDateFraisDescCreatedAtDesc(MISSION_ID))
                .thenReturn(List.of());
    }

    private static byte[] pdfBytes() {
        return "%PDF-1.4\n".getBytes();
    }

    @Test
    void creer_refuseWhenRetourBeforeDepart() {
        when(salarieRepository.findById(SALARIE_ID)).thenReturn(Optional.of(salarie()));

        MissionRequest bad = new MissionRequest(
                "Titre", "Destination", "FR", "Objectif",
                LocalDate.of(2026, 4, 25), LocalDate.of(2026, 4, 24), null, null);

        assertThatThrownBy(() -> service.creer(bad, ORG_ID, SALARIE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DATES_MISSION_INVALIDES");

        verify(missionRepository, never()).save(any());
    }

    @Test
    void creer_refuseWhenSalarieOrgMismatch() {
        Salarie s = salarie();
        s.setOrganisationId(UUID.randomUUID());
        when(salarieRepository.findById(SALARIE_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.creer(req(), ORG_ID, SALARIE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SALARIE_ORG_MISMATCH");
    }

    @Test
    void creer_ok_brouillon() {
        when(salarieRepository.findById(SALARIE_ID)).thenReturn(Optional.of(salarie()));
        when(missionRepository.save(any(Mission.class))).thenAnswer(inv -> {
            Mission m = inv.getArgument(0);
            m.setId(MISSION_ID);
            m.setCreatedAt(Instant.parse("2026-01-10T10:00:00Z"));
            return m;
        });
        stubGetById(mission(StatutMission.BROUILLON));

        MissionResponse out = service.creer(req(), ORG_ID, SALARIE_ID);

        assertThat(out.id()).isEqualTo(MISSION_ID);
        assertThat(out.statut()).isEqualTo("BROUILLON");
        assertThat(out.nbJours()).isEqualTo(3);
        assertThat(out.salarieNomComplet()).isEqualTo("Doe Jane");
    }

    @Test
    void update_refuseWhenNotBrouillon() {
        Mission m = mission(StatutMission.SOUMISE);
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.update(MISSION_ID, req(), ORG_ID, SALARIE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "MISSION_NON_MODIFIABLE");

        verify(missionRepository, never()).save(eq(m));
    }

    @Test
    void update_refuseWhenNotOwner() {
        Mission m = mission(StatutMission.BROUILLON);
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.update(MISSION_ID, req(), ORG_ID, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "MISSION_NOT_OWNER");
    }

    @Test
    void update_ok() {
        Mission m = mission(StatutMission.BROUILLON);
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));
        when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fraisMissionRepository.findByMission_IdOrderByDateFraisDescCreatedAtDesc(MISSION_ID))
                .thenReturn(List.of());

        MissionRequest updated = new MissionRequest(
                "Nouveau titre", "Lyon", "FR", "Formation",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3), null, null);

        MissionResponse out = service.update(MISSION_ID, updated, ORG_ID, SALARIE_ID);

        assertThat(out.titre()).isEqualTo("Nouveau titre");
        assertThat(out.destination()).isEqualTo("Lyon");
    }

    @Test
    void soumettre_refuseWhenNotBrouillon() {
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission(StatutMission.SOUMISE)));

        assertThatThrownBy(() -> service.soumettre(MISSION_ID, ORG_ID, SALARIE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "MISSION_STATUT_INVALIDE");
    }

    @Test
    void soumettre_ok() {
        Mission m = mission(StatutMission.BROUILLON);
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));
        when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fraisMissionRepository.findByMission_IdOrderByDateFraisDescCreatedAtDesc(MISSION_ID))
                .thenReturn(List.of());

        MissionResponse out = service.soumettre(MISSION_ID, ORG_ID, SALARIE_ID);

        assertThat(out.statut()).isEqualTo("SOUMISE");
        assertThat(m.getStatut()).isEqualTo(StatutMission.SOUMISE);
    }

    @Test
    void approuver_ok_avecAvance() throws Exception {
        Mission m = mission(StatutMission.SOUMISE);
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));
        when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fraisMissionRepository.findByMission_IdOrderByDateFraisDescCreatedAtDesc(MISSION_ID))
                .thenReturn(List.of());

        MissionResponse out =
                service.approuver(MISSION_ID, ACTOR_ID, new BigDecimal("50.00"), ORG_ID);

        assertThat(out.statut()).isEqualTo("APPROUVEE");
        assertThat(m.getAvanceVersee()).isEqualByComparingTo("50.00");
        verify(factureService).creer(any(), eq(null), eq(ORG_ID), eq(ACTOR_ID));
    }

    @Test
    void approuver_createsFactureForAvance_andWrapsException() throws Exception {
        Mission m = mission(StatutMission.SOUMISE);
        m.setAvanceDevise("USD");
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));
        when(factureService.creer(any(), eq(null), eq(ORG_ID), eq(ACTOR_ID))).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.approuver(MISSION_ID, ACTOR_ID, new BigDecimal("10.00"), ORG_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "MISSION_AVANCE_FACTURE_ERREUR");
    }

    @Test
    void refuser_ok() {
        Mission m = mission(StatutMission.SOUMISE);
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));
        when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fraisMissionRepository.findByMission_IdOrderByDateFraisDescCreatedAtDesc(MISSION_ID))
                .thenReturn(List.of());

        MissionResponse out = service.refuser(MISSION_ID, "Budget insuffisant", ORG_ID);

        assertThat(out.statut()).isEqualTo("ANNULEE");
        assertThat(m.getMotifRefus()).isEqualTo("Budget insuffisant");
    }

    @Test
    void terminer_ok_depuisApprouvee() {
        Mission m = mission(StatutMission.APPROUVEE);
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));
        when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fraisMissionRepository.findByMission_IdOrderByDateFraisDescCreatedAtDesc(MISSION_ID))
                .thenReturn(List.of());

        assertThat(service.terminer(MISSION_ID, ORG_ID).statut()).isEqualTo("TERMINEE");
    }

    @Test
    void terminer_refuseSiBrouillon() {
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission(StatutMission.BROUILLON)));

        assertThatThrownBy(() -> service.terminer(MISSION_ID, ORG_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "MISSION_STATUT_INVALIDE");
    }

    @Test
    void list_ok() {
        Mission m = mission(StatutMission.BROUILLON);
        when(missionRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(m), PageRequest.of(0, 20), 1));
        when(fraisMissionRepository.findByMission_IdOrderByDateFraisDescCreatedAtDesc(MISSION_ID))
                .thenReturn(List.of());

        var page = service.list(ORG_ID, "BROUILLON", SALARIE_ID, null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).titre()).isEqualTo("Mission X");
    }

    @Test
    void getById_notFoundAutreOrg() {
        Mission m = mission(StatutMission.BROUILLON);
        m.setOrganisationId(UUID.randomUUID());
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.getById(MISSION_ID, ORG_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "MISSION_ORG_MISMATCH");
    }

    @Test
    void uploadOrdreMission_ok() throws Exception {
        Mission m = mission(StatutMission.APPROUVEE);
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));
        when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "ordre.pdf", "application/pdf", pdfBytes());
        String url = service.uploadOrdreMission(MISSION_ID, file, ORG_ID);

        assertThat(url).isEqualTo("/api/v1/missions/" + MISSION_ID + "/ordre-mission");
        assertThat(m.getOrdreMissionUrl()).contains("/ordre/");
        verify(minioStorageService).upload(any(), any(), anyLong(), eq("application/pdf"));
    }

    @Test
    void uploadOrdreMission_refuseSiPasPdf() {
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission(StatutMission.APPROUVEE)));
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.uploadOrdreMission(MISSION_ID, file, ORG_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FICHIER_TYPE_INVALIDE");
    }

    @Test
    void uploadRapport_ok() throws Exception {
        Mission m = mission(StatutMission.EN_COURS);
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));
        when(missionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", pdfBytes());
        assertThat(service.uploadRapport(MISSION_ID, file, ORG_ID))
                .isEqualTo("/api/v1/missions/" + MISSION_ID + "/rapport");
    }

    @Test
    void ajouterFrais_ok() throws Exception {
        Mission m = mission(StatutMission.APPROUVEE);
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));
        when(tauxChangeService.tauxVersEur(ORG_ID, "USD", LocalDate.of(2026, 4, 25)))
                .thenReturn(new BigDecimal("0.90"));
        when(fraisMissionRepository.save(any(FraisMission.class))).thenAnswer(inv -> {
            FraisMission f = inv.getArgument(0);
            f.setId(FRAIS_ID);
            return f;
        });

        FraisRequest fr = new FraisRequest(
                "TRANSPORT", "Taxi", LocalDate.of(2026, 4, 25), new BigDecimal("20.00"), "USD");
        MockMultipartFile just =
                new MockMultipartFile("justificatif", "j.pdf", "application/pdf", pdfBytes());

        FraisResponse out = service.ajouterFrais(MISSION_ID, fr, just, ORG_ID);

        assertThat(out.id()).isEqualTo(FRAIS_ID);
        assertThat(out.statut()).isEqualTo("SOUMIS");
        assertThat(out.montantEur()).isEqualByComparingTo("18.00");
        assertThat(out.justificatifUrl()).contains("/justificatif");
    }

    @Test
    void ajouterFrais_refuseDateHorsMission() {
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission(StatutMission.APPROUVEE)));
        FraisRequest fr = new FraisRequest(
                "HOTEL", "Nuit", LocalDate.of(2026, 5, 1), new BigDecimal("80.00"), "EUR");

        assertThatThrownBy(() -> service.ajouterFrais(MISSION_ID, fr, null, ORG_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DATE_FRAIS_HORS_MISSION");
    }

    @Test
    void validerFrais_setsMissionInProgress_whenMissionWasApproved() {
        Mission m = mission(StatutMission.APPROUVEE);
        FraisMission f = new FraisMission();
        f.setId(FRAIS_ID);
        f.setMission(m);
        f.setStatut(StatutFrais.SOUMIS);
        f.setMontant(new BigDecimal("10.00"));
        f.setTauxChangeEur(BigDecimal.ONE);
        f.setDevise("EUR");
        f.setTypeFrais("REPAS");
        f.setDescription("Déjeuner");
        f.setDateFrais(LocalDate.of(2026, 4, 25));

        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));
        when(fraisMissionRepository.findByIdAndMission_Id(FRAIS_ID, MISSION_ID)).thenReturn(Optional.of(f));

        service.validerFrais(MISSION_ID, FRAIS_ID, ORG_ID);

        assertThat(f.getStatut()).isEqualTo(StatutFrais.VALIDE);
        assertThat(m.getStatut()).isEqualTo(StatutMission.EN_COURS);
        verify(fraisMissionRepository).save(f);
        verify(missionRepository).save(m);
    }

    @Test
    void rembourserFrais_ok() throws Exception {
        Mission m = mission(StatutMission.EN_COURS);
        FraisMission f = new FraisMission();
        f.setId(FRAIS_ID);
        f.setMission(m);
        f.setStatut(StatutFrais.VALIDE);
        f.setMontant(new BigDecimal("30.00"));
        f.setTauxChangeEur(BigDecimal.ONE);
        f.setDevise("EUR");
        f.setTypeFrais("HOTEL");
        f.setDescription("Nuit");
        f.setDateFrais(LocalDate.of(2026, 4, 25));
        f.setJustificatifUrl("missions/" + ORG_ID + "/" + MISSION_ID + "/frais/x/j.pdf");

        when(fraisMissionRepository.findByIdAndMission_Id(FRAIS_ID, MISSION_ID)).thenReturn(Optional.of(f));
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));
        when(minioStorageService.download(f.getJustificatifUrl()))
                .thenReturn(new MinioStorageService.Download(
                        f.getJustificatifUrl(), "application/pdf", 5, new ByteArrayInputStream(pdfBytes())));
        when(fraisMissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FraisResponse out = service.rembourserFrais(MISSION_ID, FRAIS_ID, ORG_ID, ACTOR_ID);

        assertThat(out.statut()).isEqualTo("REMBOURSE");
        verify(factureService).creer(any(), any(), eq(ORG_ID), eq(ACTOR_ID));
    }

    @Test
    void rembourserFrais_refuseSansJustificatif() {
        Mission m = mission(StatutMission.EN_COURS);
        FraisMission f = new FraisMission();
        f.setId(FRAIS_ID);
        f.setMission(m);
        f.setStatut(StatutFrais.VALIDE);
        f.setMontant(BigDecimal.TEN);
        f.setTauxChangeEur(BigDecimal.ONE);

        when(fraisMissionRepository.findByIdAndMission_Id(FRAIS_ID, MISSION_ID)).thenReturn(Optional.of(f));
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.rembourserFrais(MISSION_ID, FRAIS_ID, ORG_ID, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "JUSTIFICATIF_REQUIS");
    }

    @Test
    void calculerTotalEtSolde() {
        Mission m = mission(StatutMission.EN_COURS);
        m.setAvanceVersee(new BigDecimal("20.00"));
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));

        FraisMission f1 = new FraisMission();
        f1.setMontant(new BigDecimal("30.00"));
        f1.setTauxChangeEur(BigDecimal.ONE);
        FraisMission f2 = new FraisMission();
        f2.setMontant(new BigDecimal("15.00"));
        f2.setTauxChangeEur(BigDecimal.ONE);
        when(fraisMissionRepository.findByMission_IdAndStatut(MISSION_ID, StatutFrais.VALIDE))
                .thenReturn(List.of(f1, f2));

        assertThat(service.calculerTotalFrais(MISSION_ID, ORG_ID)).isEqualByComparingTo("45.00");
        assertThat(service.calculerSolde(MISSION_ID, ORG_ID)).isEqualByComparingTo("25.00");
    }

    @Test
    void getOrdreObjectName_ok() {
        Mission m = mission(StatutMission.APPROUVEE);
        m.setOrdreMissionUrl("missions/" + ORG_ID + "/" + MISSION_ID + "/ordre/o.pdf");
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(m));

        assertThat(service.getOrdreObjectName(MISSION_ID, ORG_ID)).isEqualTo(m.getOrdreMissionUrl());
    }

    @Test
    void getOrdreObjectName_absent() {
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission(StatutMission.APPROUVEE)));

        assertThatThrownBy(() -> service.getOrdreObjectName(MISSION_ID, ORG_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ORDRE_MISSION_ABSENT");
    }

    @Test
    void getFraisJustificatifObjectName_ok() {
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission(StatutMission.EN_COURS)));
        FraisMission f = new FraisMission();
        f.setJustificatifUrl("missions/" + ORG_ID + "/" + MISSION_ID + "/frais/x/j.pdf");
        when(fraisMissionRepository.findByIdAndMission_Id(FRAIS_ID, MISSION_ID)).thenReturn(Optional.of(f));

        assertThat(service.getFraisJustificatifObjectName(MISSION_ID, FRAIS_ID, ORG_ID))
                .isEqualTo(f.getJustificatifUrl());
    }

    @Test
    void getFrais_ok() {
        when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission(StatutMission.EN_COURS)));
        FraisMission f = new FraisMission();
        f.setId(FRAIS_ID);
        f.setMission(mission(StatutMission.EN_COURS));
        f.setTypeFrais("REPAS");
        f.setDescription("Midi");
        f.setDateFrais(LocalDate.of(2026, 4, 25));
        f.setMontant(new BigDecimal("12.00"));
        f.setDevise("EUR");
        f.setTauxChangeEur(BigDecimal.ONE);
        f.setStatut(StatutFrais.VALIDE);
        when(fraisMissionRepository.findByIdAndMission_Id(FRAIS_ID, MISSION_ID)).thenReturn(Optional.of(f));

        assertThat(service.getFrais(MISSION_ID, FRAIS_ID, ORG_ID).typeFrais()).isEqualTo("REPAS");
    }
}

package com.app.modules.missions.service;

import com.app.modules.finance.service.FactureService;
import com.app.modules.finance.service.TauxChangeService;
import com.app.modules.missions.dto.MissionRequest;
import com.app.modules.missions.entity.Mission;
import com.app.modules.missions.entity.StatutFrais;
import com.app.modules.missions.entity.StatutMission;
import com.app.modules.missions.entity.FraisMission;
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

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock MissionRepository missionRepository;
    @Mock FraisMissionRepository fraisMissionRepository;
    @Mock SalarieRepository salarieRepository;
    @Mock MinioStorageService minioStorageService;
    @Mock TauxChangeService tauxChangeService;
    @Mock FactureService factureService;

    @InjectMocks MissionService service;

    @Test
    void creer_refuseWhenRetourBeforeDepart() {
        UUID orgId = UUID.randomUUID();
        UUID salarieId = UUID.randomUUID();
        Salarie s = new Salarie();
        s.setId(salarieId);
        s.setOrganisationId(orgId);

        when(salarieRepository.findById(salarieId)).thenReturn(Optional.of(s));

        MissionRequest req = new MissionRequest(
                "Titre",
                "Destination",
                "FR",
                "Objectif",
                LocalDate.of(2026, 4, 25),
                LocalDate.of(2026, 4, 24),
                null,
                null
        );

        assertThatThrownBy(() -> service.creer(req, orgId, salarieId))
                .isInstanceOf(BusinessException.class);

        verify(missionRepository, never()).save(any());
    }

    @Test
    void update_refuseWhenNotBrouillon() {
        UUID orgId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();

        Salarie owner = new Salarie();
        owner.setId(actorId);
        owner.setOrganisationId(orgId);

        Mission m = new Mission();
        m.setId(missionId);
        m.setOrganisationId(orgId);
        m.setSalarie(owner);
        m.setStatut(StatutMission.SOUMISE);

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(m));

        MissionRequest req = new MissionRequest(
                "T",
                "D",
                "FR",
                "O",
                LocalDate.of(2026, 4, 24),
                LocalDate.of(2026, 4, 25),
                null,
                null
        );

        assertThatThrownBy(() -> service.update(missionId, req, orgId, actorId))
                .isInstanceOf(BusinessException.class);

        verify(missionRepository, never()).save(eq(m));
    }

    @Test
    void update_refuseWhenNotOwner() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();

        Salarie owner = new Salarie();
        owner.setId(ownerId);
        owner.setOrganisationId(orgId);

        Mission m = new Mission();
        m.setId(missionId);
        m.setOrganisationId(orgId);
        m.setSalarie(owner);
        m.setStatut(StatutMission.BROUILLON);

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(m));

        MissionRequest req = new MissionRequest(
                "T",
                "D",
                "FR",
                "O",
                LocalDate.of(2026, 4, 24),
                LocalDate.of(2026, 4, 25),
                null,
                null
        );

        assertThatThrownBy(() -> service.update(missionId, req, orgId, actorId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void soumettre_refuseWhenNotBrouillon() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();

        Salarie owner = new Salarie();
        owner.setId(ownerId);
        owner.setOrganisationId(orgId);

        Mission m = new Mission();
        m.setId(missionId);
        m.setOrganisationId(orgId);
        m.setSalarie(owner);
        m.setStatut(StatutMission.SOUMISE);

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.soumettre(missionId, orgId, ownerId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void approuver_createsFactureForAvance_andWrapsException() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID approbateurId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();

        Salarie s = new Salarie();
        s.setId(UUID.randomUUID());
        s.setOrganisationId(orgId);
        s.setNom("Doe");
        s.setPrenom("Jane");

        Mission m = new Mission();
        m.setId(missionId);
        m.setOrganisationId(orgId);
        m.setSalarie(s);
        m.setStatut(StatutMission.SOUMISE);
        m.setTitre("Mission X");
        m.setAvanceDevise("USD");

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(m));
        when(factureService.creer(any(), eq(null), eq(orgId), eq(approbateurId))).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.approuver(missionId, approbateurId, new java.math.BigDecimal("10.00"), orgId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validerFrais_setsMissionInProgress_whenMissionWasApproved() {
        UUID orgId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        UUID fraisId = UUID.randomUUID();

        Salarie s = new Salarie();
        s.setId(UUID.randomUUID());
        s.setOrganisationId(orgId);

        Mission m = new Mission();
        m.setId(missionId);
        m.setOrganisationId(orgId);
        m.setSalarie(s);
        m.setStatut(StatutMission.APPROUVEE);

        FraisMission f = new FraisMission();
        f.setId(fraisId);
        f.setMission(m);
        f.setStatut(StatutFrais.SOUMIS);

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(m));
        when(fraisMissionRepository.findByIdAndMission_Id(fraisId, missionId)).thenReturn(Optional.of(f));

        service.validerFrais(missionId, fraisId, orgId);

        verify(fraisMissionRepository).save(f);
        verify(missionRepository).save(m);
    }
}


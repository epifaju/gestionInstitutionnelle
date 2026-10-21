package com.app.modules.templates.service;

import com.app.modules.courriers.dto.CourrierResponse;
import com.app.modules.courriers.service.CourrierService;
import com.app.modules.missions.dto.MissionResponse;
import com.app.modules.missions.service.MissionService;
import com.app.modules.rh.service.ContratService;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateDataServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @Mock
    private MissionService missionService;
    @Mock
    private ContratService contratService;
    @Mock
    private CourrierService courrierService;

    @Test
    void buildValues_refuseSujetInvalide() {
        TemplateDataService svc = new TemplateDataService(missionService, contratService, courrierService);
        assertThatThrownBy(() -> svc.buildValues(null, UUID.randomUUID(), orgId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TEMPLATE_SUBJECT_INVALIDE");
    }

    @Test
    void buildValues_mission_remplitChamps() {
        UUID missionId = UUID.randomUUID();
        when(missionService.getById(missionId, orgId))
                .thenReturn(new MissionResponse(
                        missionId,
                        "Titre",
                        "Dest",
                        "FR",
                        "Objectif",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 2),
                        2,
                        "EN_COURS",
                        "Jean Dupont",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        null,
                        null,
                        null
                ));

        TemplateDataService svc = new TemplateDataService(missionService, contratService, courrierService);
        Map<String, String> out = svc.buildValues("Mission", missionId, orgId);
        assertThat(out.get("mission.id")).isEqualTo(missionId.toString());
        assertThat(out.get("mission.titre")).isEqualTo("Titre");
    }

    @Test
    void buildValues_courrier_remplitChampsRegistre() {
        UUID courrierId = UUID.randomUUID();
        when(courrierService.getById(courrierId, orgId))
                .thenReturn(new CourrierResponse(
                        courrierId,
                        "SORTIE",
                        "ENREGISTRE",
                        "REF-42",
                        "Note verbale",
                        "Ambassade",
                        "MAE",
                        LocalDate.of(2026, 4, 1),
                        null,
                        LocalDate.of(2026, 4, 2),
                        "COURRIER_POSTAL",
                        "Urgent",
                        null,
                        null,
                        Instant.parse("2026-04-01T10:00:00Z"),
                        Instant.parse("2026-04-01T10:00:00Z")));

        TemplateDataService svc = new TemplateDataService(missionService, contratService, courrierService);
        Map<String, String> out = svc.buildValues("COURRIER", courrierId, orgId);

        assertThat(out.get("courrier.id")).isEqualTo(courrierId.toString());
        assertThat(out.get("courrier.objet")).isEqualTo("Note verbale");
        assertThat(out.get("courrier.reference")).isEqualTo("REF-42");
        assertThat(out.get("courrier.destinataire")).isEqualTo("MAE");
        assertThat(out.get("courrier.sens")).isEqualTo("SORTIE");
        assertThat(out.get("courrier.organisationId")).isEqualTo(orgId.toString());
        assertThat(out.get("courrier.subjectId")).isEqualTo(courrierId.toString());
    }

    @Test
    void buildValues_courrier_fallbackSiNonTrouve() {
        when(courrierService.getById(orgId, orgId))
                .thenThrow(BusinessException.notFound("COURRIER_NON_TROUVE"));

        TemplateDataService svc = new TemplateDataService(missionService, contratService, courrierService);
        Map<String, String> out = svc.buildValues("COURRIER", orgId, orgId);

        assertThat(out.get("courrier.organisationId")).isEqualTo(orgId.toString());
        assertThat(out.get("courrier.subjectId")).isEqualTo(orgId.toString());
        assertThat(out.containsKey("courrier.objet")).isFalse();
    }
}

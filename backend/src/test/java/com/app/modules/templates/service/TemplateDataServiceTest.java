package com.app.modules.templates.service;

import com.app.modules.missions.dto.MissionResponse;
import com.app.modules.missions.service.MissionService;
import com.app.modules.rh.service.ContratService;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;

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

    @Test
    void buildValues_refuseSujetInvalide() {
        TemplateDataService svc = new TemplateDataService(missionService, contratService);
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

        TemplateDataService svc = new TemplateDataService(missionService, contratService);
        Map<String, String> out = svc.buildValues("Mission", missionId, orgId);
        assertThat(out.get("mission.id")).isEqualTo(missionId.toString());
        assertThat(out.get("mission.titre")).isEqualTo("Titre");
    }
}


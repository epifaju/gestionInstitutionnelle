package com.app.modules.workflow.service;

import com.app.modules.workflow.dto.UpsertWorkflowDefinitionRequest;
import com.app.modules.workflow.dto.UpsertWorkflowRuleRequest;
import com.app.modules.workflow.entity.WorkflowDefinition;
import com.app.modules.workflow.entity.WorkflowRule;
import com.app.modules.workflow.repository.WorkflowDefinitionRepository;
import com.app.modules.workflow.repository.WorkflowRuleRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowAdminServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @Mock
    private WorkflowDefinitionRepository definitionRepository;
    @Mock
    private WorkflowRuleRepository ruleRepository;

    @InjectMocks
    private WorkflowAdminService service;

    @Test
    void get_definitionAbsente_lanceNotFound() {
        when(definitionRepository.findByOrganisationIdAndProcessKey(orgId, "X")).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.get(orgId, "X"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WORKFLOW_DEFINITION_ABSENTE");
    }

    @Test
    void upsert_creeDefinitionEtNormaliseKeyEtLevels() {
        when(definitionRepository.findByOrganisationIdAndProcessKey(eq(orgId), any())).thenReturn(Optional.empty());
        when(definitionRepository.save(any(WorkflowDefinition.class))).thenAnswer(inv -> {
            WorkflowDefinition d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(ruleRepository.save(any(WorkflowRule.class))).thenAnswer(inv -> inv.getArgument(0));

        UpsertWorkflowDefinitionRequest req = new UpsertWorkflowDefinitionRequest(
                "mission_validate",
                true,
                List.of(new UpsertWorkflowRuleRequest(null, null, 3, "rh", "financier")));

        var dto = service.upsert(orgId, req);
        assertThat(dto.processKey()).isEqualTo("MISSION_VALIDATE");
        assertThat(dto.rules()).hasSize(1);
        assertThat(dto.rules().getFirst().levels()).isEqualTo(2); // clamped to max 2
        assertThat(dto.rules().getFirst().level1Role()).isEqualTo("RH");
        assertThat(dto.rules().getFirst().level2Role()).isEqualTo("FINANCIER");
    }
}


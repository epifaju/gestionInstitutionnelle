package com.app.modules.workflow.service;

import com.app.modules.workflow.entity.WorkflowDefinition;
import com.app.modules.workflow.entity.WorkflowDelegation;
import com.app.modules.workflow.entity.WorkflowInstance;
import com.app.modules.workflow.entity.WorkflowInstanceStatus;
import com.app.modules.workflow.entity.WorkflowRule;
import com.app.modules.workflow.repository.WorkflowActionRepository;
import com.app.modules.workflow.repository.WorkflowDefinitionRepository;
import com.app.modules.workflow.repository.WorkflowDelegationRepository;
import com.app.modules.workflow.repository.WorkflowInstanceRepository;
import com.app.modules.workflow.repository.WorkflowRuleRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineServiceTest {

    @Mock WorkflowDefinitionRepository definitionRepository;
    @Mock WorkflowRuleRepository ruleRepository;
    @Mock WorkflowInstanceRepository instanceRepository;
    @Mock WorkflowActionRepository actionRepository;
    @Mock WorkflowDelegationRepository delegationRepository;

    @InjectMocks WorkflowEngineService service;

    @Test
    void resolveRule_picksFirstMatchingRange() {
        UUID orgId = UUID.randomUUID();
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(UUID.randomUUID());
        def.setOrganisationId(orgId);
        def.setProcessKey("FRAIS_REIMBURSE");
        def.setEnabled(true);

        WorkflowRule r1 = new WorkflowRule();
        r1.setDefinition(def);
        r1.setLevels(1);
        r1.setLevel1Role("RH");
        r1.setMinAmountEur(new BigDecimal("0.00"));
        r1.setMaxAmountEur(new BigDecimal("99.99"));

        WorkflowRule r2 = new WorkflowRule();
        r2.setDefinition(def);
        r2.setLevels(2);
        r2.setLevel1Role("RH");
        r2.setLevel2Role("FINANCIER");
        r2.setMinAmountEur(new BigDecimal("100.00"));
        r2.setMaxAmountEur(null);

        when(definitionRepository.findByOrganisationIdAndProcessKey(orgId, "FRAIS_REIMBURSE")).thenReturn(Optional.of(def));
        when(ruleRepository.findByDefinition_IdOrderByCreatedAtAsc(def.getId())).thenReturn(List.of(r1, r2));

        WorkflowRuleMatch low = service.resolveRule(orgId, "FRAIS_REIMBURSE", new BigDecimal("10.00"));
        assertThat(low.levels()).isEqualTo(1);
        assertThat(low.level1Role()).isEqualTo("RH");

        WorkflowRuleMatch hi = service.resolveRule(orgId, "FRAIS_REIMBURSE", new BigDecimal("150.00"));
        assertThat(hi.levels()).isEqualTo(2);
        assertThat(hi.level1Role()).isEqualTo("RH");
        assertThat(hi.level2Role()).isEqualTo("FINANCIER");
    }

    @Test
    void approve_setsPendingLevel2_whenTwoLevels() {
        UUID orgId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        WorkflowInstance inst = new WorkflowInstance();
        inst.setId(instanceId);
        inst.setOrganisationId(orgId);
        inst.setStatus(WorkflowInstanceStatus.PENDING);
        inst.setCurrentLevel(1);

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(inst));
        when(instanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkflowRuleMatch rule = new WorkflowRuleMatch(2, "RH", "FINANCIER", null, null);

        WorkflowInstance updated = service.approve(orgId, instanceId, rule, actorId, "RH", null);
        assertThat(updated.getStatus()).isEqualTo(WorkflowInstanceStatus.PENDING);
        assertThat(updated.getCurrentLevel()).isEqualTo(2);
    }

    @Test
    void isActorAllowed_acceptsActiveDelegation() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WorkflowDelegation d = new WorkflowDelegation();
        d.setOrganisationId(orgId);
        d.setFromRole("FINANCIER");
        d.setToUserId(userId);
        when(delegationRepository.findActiveByRole(eq(orgId), eq("FINANCIER"), any(OffsetDateTime.class))).thenReturn(Optional.of(d));

        assertThat(service.isActorAllowed(orgId, "FINANCIER", userId, "RH")).isTrue();
    }

    @Test
    void approve_forbidden_whenWrongRoleAndNoDelegation() {
        UUID orgId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        WorkflowInstance inst = new WorkflowInstance();
        inst.setId(instanceId);
        inst.setOrganisationId(orgId);
        inst.setStatus(WorkflowInstanceStatus.PENDING);
        inst.setCurrentLevel(1);

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(inst));
        when(delegationRepository.findActiveByRole(eq(orgId), eq("RH"), any(OffsetDateTime.class))).thenReturn(Optional.empty());

        WorkflowRuleMatch rule = new WorkflowRuleMatch(1, "RH", null, null, null);
        assertThatThrownBy(() -> service.approve(orgId, instanceId, rule, actorId, "FINANCIER", null))
                .isInstanceOf(BusinessException.class);
    }
}


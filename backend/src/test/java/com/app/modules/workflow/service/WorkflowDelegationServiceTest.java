package com.app.modules.workflow.service;

import com.app.modules.workflow.entity.WorkflowDelegation;
import com.app.modules.workflow.repository.WorkflowDelegationRepository;
import com.app.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowDelegationServiceTest {

    private static final UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @Mock
    private WorkflowDelegationRepository delegationRepository;

    @InjectMocks
    private WorkflowDelegationService service;

    @Test
    void create_refuseDatesInvalides() {
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.minusDays(1);
        assertThatThrownBy(() -> service.create(orgId, "RH", UUID.randomUUID(), start, end, "x", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DELEGATION_DATES_INVALIDES");
    }

    @Test
    void delete_refuseSiOrgMismatch() {
        WorkflowDelegation d = new WorkflowDelegation();
        d.setId(UUID.randomUUID());
        d.setOrganisationId(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"));
        when(delegationRepository.findById(d.getId())).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.delete(orgId, d.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DELEGATION_ORG_MISMATCH");
    }
}


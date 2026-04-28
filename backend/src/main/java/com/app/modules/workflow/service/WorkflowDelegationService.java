package com.app.modules.workflow.service;

import com.app.modules.workflow.entity.WorkflowDelegation;
import com.app.modules.workflow.repository.WorkflowDelegationRepository;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowDelegationService {

    private final WorkflowDelegationRepository delegationRepository;

    @Transactional(readOnly = true)
    public List<WorkflowDelegation> listActiveForUser(UUID orgId, UUID userId) {
        return delegationRepository.findActiveForUser(orgId, userId, OffsetDateTime.now());
    }

    @Transactional
    public WorkflowDelegation create(UUID orgId, String fromRole, UUID toUserId, OffsetDateTime startAt, OffsetDateTime endAt, String reason, UUID createdBy) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw BusinessException.badRequest("DELEGATION_DATES_INVALIDES");
        }
        WorkflowDelegation d = new WorkflowDelegation();
        d.setOrganisationId(orgId);
        d.setFromRole(fromRole == null ? "" : fromRole.trim().toUpperCase(Locale.ROOT));
        d.setToUserId(toUserId);
        d.setStartAt(startAt);
        d.setEndAt(endAt);
        d.setReason(reason);
        d.setCreatedBy(createdBy);
        return delegationRepository.save(d);
    }

    @Transactional
    public void delete(UUID orgId, UUID id) {
        WorkflowDelegation d = delegationRepository.findById(id).orElseThrow(() -> BusinessException.notFound("DELEGATION_ABSENTE"));
        if (!orgId.equals(d.getOrganisationId())) throw BusinessException.forbidden("DELEGATION_ORG_MISMATCH");
        delegationRepository.delete(d);
    }
}


package com.app.modules.workflow.service;

import com.app.modules.workflow.entity.WorkflowAction;
import com.app.modules.workflow.entity.WorkflowDecision;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowEngineService {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowRuleRepository ruleRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowActionRepository actionRepository;
    private final WorkflowDelegationRepository delegationRepository;

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID orgId, String processKey) {
        return definitionRepository.findByOrganisationIdAndProcessKey(orgId, processKey)
                .map(WorkflowDefinition::isEnabled)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowDefinition> getDefinition(UUID orgId, String processKey) {
        return definitionRepository.findByOrganisationIdAndProcessKey(orgId, processKey);
    }

    @Transactional(readOnly = true)
    public WorkflowRuleMatch resolveRule(UUID orgId, String processKey, BigDecimal amountEur) {
        WorkflowDefinition def = definitionRepository.findByOrganisationIdAndProcessKey(orgId, processKey)
                .orElseThrow(() -> BusinessException.notFound("WORKFLOW_DEFINITION_ABSENTE"));
        List<WorkflowRule> rules = ruleRepository.findByDefinition_IdOrderByCreatedAtAsc(def.getId());
        BigDecimal x = amountEur == null ? BigDecimal.ZERO : amountEur;
        WorkflowRule best = null;
        for (WorkflowRule r : rules) {
            BigDecimal min = r.getMinAmountEur();
            BigDecimal max = r.getMaxAmountEur();
            boolean okMin = (min == null) || x.compareTo(min) >= 0;
            boolean okMax = (max == null) || x.compareTo(max) <= 0;
            if (okMin && okMax) {
                best = r;
                break;
            }
        }
        if (best == null) {
            // default: 1 niveau, rôle RH (safe default for HR-like workflows)
            return new WorkflowRuleMatch(1, "RH", null, null, null);
        }
        int levels = Math.max(1, Math.min(2, best.getLevels()));
        String l1 = normRole(best.getLevel1Role());
        String l2 = levels == 2 ? normRole(best.getLevel2Role()) : null;
        if (l1 == null || l1.isBlank()) throw BusinessException.badRequest("WORKFLOW_RULE_INVALIDE");
        if (levels == 2 && (l2 == null || l2.isBlank())) throw BusinessException.badRequest("WORKFLOW_RULE_INVALIDE");
        return new WorkflowRuleMatch(levels, l1, l2, best.getMinAmountEur(), best.getMaxAmountEur());
    }

    @Transactional
    public WorkflowInstance submitIfAbsent(UUID orgId, String processKey, String subjectType, UUID subjectId, BigDecimal amountEur, UUID createdBy) {
        WorkflowInstance inst =
                instanceRepository
                        .findByOrganisationIdAndProcessKeyAndSubjectTypeAndSubjectId(orgId, processKey, subjectType, subjectId)
                        .orElse(null);
        if (inst != null) return inst;
        WorkflowInstance w = new WorkflowInstance();
        w.setOrganisationId(orgId);
        w.setProcessKey(processKey);
        w.setSubjectType(subjectType);
        w.setSubjectId(subjectId);
        w.setAmountEur(amountEur);
        w.setStatus(WorkflowInstanceStatus.PENDING);
        w.setCurrentLevel(1);
        w.setCreatedBy(createdBy);
        return instanceRepository.save(w);
    }

    @Transactional(readOnly = true)
    public List<WorkflowInstance> listBySubject(UUID orgId, String subjectType, UUID subjectId) {
        return instanceRepository.findByOrganisationIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(orgId, subjectType, subjectId);
    }

    @Transactional(readOnly = true)
    public WorkflowInstance getInstance(UUID orgId, UUID instanceId) {
        WorkflowInstance inst = instanceRepository.findById(instanceId)
                .orElseThrow(() -> BusinessException.notFound("WORKFLOW_INSTANCE_ABSENTE"));
        if (!orgId.equals(inst.getOrganisationId())) throw BusinessException.forbidden("WORKFLOW_ORG_MISMATCH");
        return inst;
    }

    @Transactional(readOnly = true)
    public List<WorkflowAction> listActions(UUID instanceId) {
        return actionRepository.findByInstance_IdOrderByCreatedAtAsc(instanceId);
    }

    @Transactional
    public WorkflowInstance approve(UUID orgId, UUID instanceId, WorkflowRuleMatch rule, UUID actorUserId, String actorRole, String comment) {
        WorkflowInstance inst = instanceRepository.findById(instanceId)
                .orElseThrow(() -> BusinessException.notFound("WORKFLOW_INSTANCE_ABSENTE"));
        if (!orgId.equals(inst.getOrganisationId())) throw BusinessException.forbidden("WORKFLOW_ORG_MISMATCH");
        if (inst.getStatus() != WorkflowInstanceStatus.PENDING) throw BusinessException.badRequest("WORKFLOW_STATUT_INVALIDE");

        int lvl = inst.getCurrentLevel();
        String expectedRole = (lvl == 1) ? rule.level1Role() : rule.level2Role();
        if (!isActorAllowed(orgId, expectedRole, actorUserId, actorRole)) {
            throw BusinessException.forbidden("WORKFLOW_VALIDATION_NON_AUTORISEE");
        }

        WorkflowAction a = new WorkflowAction();
        a.setInstance(inst);
        a.setLevel(lvl);
        a.setActorUserId(actorUserId);
        a.setActorRole(normRole(actorRole));
        a.setDecision(WorkflowDecision.APPROVE);
        a.setComment(comment);
        actionRepository.save(a);

        if (rule.levels() == 2 && lvl == 1) {
            inst.setCurrentLevel(2);
            return instanceRepository.save(inst);
        }
        inst.setStatus(WorkflowInstanceStatus.APPROVED);
        inst.setCompletedAt(Instant.now());
        return instanceRepository.save(inst);
    }

    @Transactional
    public WorkflowInstance reject(UUID orgId, UUID instanceId, WorkflowRuleMatch rule, UUID actorUserId, String actorRole, String comment) {
        WorkflowInstance inst = instanceRepository.findById(instanceId)
                .orElseThrow(() -> BusinessException.notFound("WORKFLOW_INSTANCE_ABSENTE"));
        if (!orgId.equals(inst.getOrganisationId())) throw BusinessException.forbidden("WORKFLOW_ORG_MISMATCH");
        if (inst.getStatus() != WorkflowInstanceStatus.PENDING) throw BusinessException.badRequest("WORKFLOW_STATUT_INVALIDE");

        int lvl = inst.getCurrentLevel();
        String expectedRole = (lvl == 1) ? rule.level1Role() : rule.level2Role();
        if (!isActorAllowed(orgId, expectedRole, actorUserId, actorRole)) {
            throw BusinessException.forbidden("WORKFLOW_VALIDATION_NON_AUTORISEE");
        }

        WorkflowAction a = new WorkflowAction();
        a.setInstance(inst);
        a.setLevel(lvl);
        a.setActorUserId(actorUserId);
        a.setActorRole(normRole(actorRole));
        a.setDecision(WorkflowDecision.REJECT);
        a.setComment(comment);
        actionRepository.save(a);

        inst.setStatus(WorkflowInstanceStatus.REJECTED);
        inst.setCompletedAt(Instant.now());
        return instanceRepository.save(inst);
    }

    @Transactional(readOnly = true)
    public boolean isActorAllowed(UUID orgId, String expectedRole, UUID actorUserId, String actorRole) {
        String exp = normRole(expectedRole);
        String act = normRole(actorRole);
        if (exp != null && exp.equals(act)) return true;

        // délégation active du rôle attendu vers ce user
        OffsetDateTime now = OffsetDateTime.now();
        WorkflowDelegation del = delegationRepository.findActiveByRole(orgId, exp, now).orElse(null);
        return del != null && actorUserId != null && actorUserId.equals(del.getToUserId());
    }

    private static String normRole(String r) {
        if (r == null) return null;
        return r.trim().toUpperCase(Locale.ROOT);
    }
}


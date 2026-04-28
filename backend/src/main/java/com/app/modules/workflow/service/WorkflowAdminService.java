package com.app.modules.workflow.service;

import com.app.modules.workflow.dto.UpsertWorkflowDefinitionRequest;
import com.app.modules.workflow.dto.UpsertWorkflowRuleRequest;
import com.app.modules.workflow.dto.WorkflowDefinitionDto;
import com.app.modules.workflow.dto.WorkflowRuleDto;
import com.app.modules.workflow.entity.WorkflowDefinition;
import com.app.modules.workflow.entity.WorkflowRule;
import com.app.modules.workflow.repository.WorkflowDefinitionRepository;
import com.app.modules.workflow.repository.WorkflowRuleRepository;
import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowAdminService {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowRuleRepository ruleRepository;

    @Transactional(readOnly = true)
    public List<WorkflowDefinitionDto> list(UUID orgId) {
        return definitionRepository.findAll().stream()
                .filter(d -> orgId.equals(d.getOrganisationId()))
                .map(d -> toDto(d, ruleRepository.findByDefinition_IdOrderByCreatedAtAsc(d.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowDefinitionDto get(UUID orgId, String processKey) {
        WorkflowDefinition d = definitionRepository.findByOrganisationIdAndProcessKey(orgId, processKey)
                .orElseThrow(() -> BusinessException.notFound("WORKFLOW_DEFINITION_ABSENTE"));
        return toDto(d, ruleRepository.findByDefinition_IdOrderByCreatedAtAsc(d.getId()));
    }

    @Transactional
    public WorkflowDefinitionDto upsert(UUID orgId, UpsertWorkflowDefinitionRequest req) {
        String key = req.processKey().trim().toUpperCase(Locale.ROOT);
        WorkflowDefinition d = definitionRepository.findByOrganisationIdAndProcessKey(orgId, key).orElse(null);
        if (d == null) {
            d = new WorkflowDefinition();
            d.setOrganisationId(orgId);
            d.setProcessKey(key);
        }
        d.setEnabled(Boolean.TRUE.equals(req.enabled()));
        d.setUpdatedAt(Instant.now());
        d = definitionRepository.save(d);

        // Replace rules (simple + safe, avoids partial updates). If null -> keep existing.
        if (req.rules() != null) {
            List<WorkflowRule> existing = ruleRepository.findByDefinition_IdOrderByCreatedAtAsc(d.getId());
            if (!existing.isEmpty()) {
                ruleRepository.deleteAll(existing);
            }
            List<WorkflowRule> saved = new ArrayList<>();
            for (UpsertWorkflowRuleRequest r : req.rules()) {
                WorkflowRule wr = new WorkflowRule();
                wr.setDefinition(d);
                wr.setMinAmountEur(r.minAmountEur());
                wr.setMaxAmountEur(r.maxAmountEur());
                int levels = r.levels() == null ? 1 : r.levels();
                wr.setLevels(Math.max(1, Math.min(2, levels)));
                wr.setLevel1Role(r.level1Role().trim().toUpperCase(Locale.ROOT));
                wr.setLevel2Role(r.level2Role() != null ? r.level2Role().trim().toUpperCase(Locale.ROOT) : null);
                saved.add(ruleRepository.save(wr));
            }
            return toDto(d, saved);
        }
        return toDto(d, ruleRepository.findByDefinition_IdOrderByCreatedAtAsc(d.getId()));
    }

    private static WorkflowDefinitionDto toDto(WorkflowDefinition d, List<WorkflowRule> rules) {
        List<WorkflowRuleDto> rr = rules.stream()
                .map(r -> new WorkflowRuleDto(
                        r.getId(),
                        r.getMinAmountEur(),
                        r.getMaxAmountEur(),
                        r.getLevels(),
                        r.getLevel1Role(),
                        r.getLevel2Role()
                ))
                .toList();
        return new WorkflowDefinitionDto(d.getId(), d.getProcessKey(), d.isEnabled(), rr);
    }
}


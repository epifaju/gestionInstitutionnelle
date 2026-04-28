package com.app.modules.workflow.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.workflow.dto.WorkflowActionDto;
import com.app.modules.workflow.dto.WorkflowDecisionRequest;
import com.app.modules.workflow.dto.WorkflowInstanceDto;
import com.app.modules.workflow.entity.WorkflowAction;
import com.app.modules.workflow.entity.WorkflowInstance;
import com.app.modules.workflow.service.WorkflowEngineService;
import com.app.modules.workflow.service.WorkflowRuleMatch;
import com.app.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowEngineService workflowEngineService;

    @GetMapping("/instances")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<WorkflowInstanceDto>>> listInstances(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam String subjectType,
            @RequestParam UUID subjectId
    ) {
        List<WorkflowInstanceDto> out = workflowEngineService.listBySubject(user.getOrganisationId(), subjectType, subjectId)
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @PostMapping("/{processKey}/{subjectType}/{subjectId}/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<WorkflowInstanceDto>> submit(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String processKey,
            @PathVariable String subjectType,
            @PathVariable UUID subjectId,
            @RequestParam(required = false) BigDecimal amountEur
    ) {
        WorkflowInstance inst = workflowEngineService.submitIfAbsent(user.getOrganisationId(), processKey, subjectType, subjectId, amountEur, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(toDto(inst)));
    }

    @PostMapping("/instances/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<WorkflowInstanceDto>> approve(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) WorkflowDecisionRequest req
    ) {
        WorkflowInstance inst = workflowEngineService.getInstance(user.getOrganisationId(), id);
        WorkflowRuleMatch rule = workflowEngineService.resolveRule(user.getOrganisationId(), inst.getProcessKey(), inst.getAmountEur());
        return ResponseEntity.ok(ApiResponse.ok(toDto(workflowEngineService.approve(
                user.getOrganisationId(),
                id,
                rule,
                user.getId(),
                user.getUtilisateur() != null && user.getUtilisateur().getRole() != null ? user.getUtilisateur().getRole().name() : "",
                req != null ? req.comment() : null
        ))));
    }

    @PostMapping("/instances/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<WorkflowInstanceDto>> reject(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) WorkflowDecisionRequest req
    ) {
        WorkflowInstance inst = workflowEngineService.getInstance(user.getOrganisationId(), id);
        WorkflowRuleMatch rule = workflowEngineService.resolveRule(user.getOrganisationId(), inst.getProcessKey(), inst.getAmountEur());
        return ResponseEntity.ok(ApiResponse.ok(toDto(workflowEngineService.reject(
                user.getOrganisationId(),
                id,
                rule,
                user.getId(),
                user.getUtilisateur() != null && user.getUtilisateur().getRole() != null ? user.getUtilisateur().getRole().name() : "",
                req != null ? req.comment() : null
        ))));
    }

    private WorkflowInstanceDto toDto(WorkflowInstance inst) {
        List<WorkflowActionDto> actions = workflowEngineService.listActions(inst.getId()).stream().map(this::toDto).toList();
        return new WorkflowInstanceDto(
                inst.getId(),
                inst.getProcessKey(),
                inst.getSubjectType(),
                inst.getSubjectId(),
                inst.getAmountEur(),
                inst.getStatus(),
                inst.getCurrentLevel(),
                inst.getCreatedAt(),
                inst.getCompletedAt(),
                actions
        );
    }

    private WorkflowActionDto toDto(WorkflowAction a) {
        return new WorkflowActionDto(
                a.getId(),
                a.getLevel(),
                a.getActorUserId(),
                a.getActorRole(),
                a.getDecision(),
                a.getComment(),
                a.getCreatedAt()
        );
    }
}


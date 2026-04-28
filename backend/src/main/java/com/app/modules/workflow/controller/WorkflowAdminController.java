package com.app.modules.workflow.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.workflow.dto.UpsertWorkflowDefinitionRequest;
import com.app.modules.workflow.dto.WorkflowDefinitionDto;
import com.app.modules.workflow.entity.WorkflowDelegation;
import com.app.modules.workflow.service.WorkflowAdminService;
import com.app.modules.workflow.service.WorkflowDelegationService;
import com.app.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/workflows")
@RequiredArgsConstructor
public class WorkflowAdminController {

    private final WorkflowAdminService adminService;
    private final WorkflowDelegationService delegationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<WorkflowDefinitionDto>>> list(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.list(user.getOrganisationId())));
    }

    @GetMapping("/{processKey}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<WorkflowDefinitionDto>> get(@AuthenticationPrincipal CustomUserDetails user, @PathVariable String processKey) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.get(user.getOrganisationId(), processKey)));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<WorkflowDefinitionDto>> upsert(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody UpsertWorkflowDefinitionRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.upsert(user.getOrganisationId(), req)));
    }

    @PostMapping("/delegations")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<WorkflowDelegation>> createDelegation(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody Map<String, Object> body
    ) {
        String fromRole = body == null ? null : (String) body.get("fromRole");
        UUID toUserId = body != null && body.get("toUserId") != null ? UUID.fromString(String.valueOf(body.get("toUserId"))) : null;
        OffsetDateTime startAt = body != null && body.get("startAt") != null ? OffsetDateTime.parse(String.valueOf(body.get("startAt"))) : null;
        OffsetDateTime endAt = body != null && body.get("endAt") != null ? OffsetDateTime.parse(String.valueOf(body.get("endAt"))) : null;
        String reason = body == null ? null : (String) body.get("reason");
        return ResponseEntity.ok(ApiResponse.ok(delegationService.create(user.getOrganisationId(), fromRole, toUserId, startAt, endAt, reason, user.getId())));
    }

    @DeleteMapping("/delegations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteDelegation(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        delegationService.delete(user.getOrganisationId(), id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true)));
    }
}


package com.app.modules.rapports.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.rapports.dto.todo.QuickActionRequest;
import com.app.modules.rapports.dto.todo.QuickActionResponse;
import com.app.modules.rapports.dto.todo.TodoDashboardResponse;
import com.app.modules.rapports.service.TodoDashboardService;
import com.app.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/todo-dashboard")
@RequiredArgsConstructor
public class TodoDashboardController {

    private final TodoDashboardService todoDashboardService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Cacheable(value = "todo-dashboard", key = "#user.organisationId.toString() + '_' + (#user.utilisateur != null && #user.utilisateur.role != null ? #user.utilisateur.role.name() : '')")
    public ResponseEntity<ApiResponse<TodoDashboardResponse>> get(
            @AuthenticationPrincipal CustomUserDetails user) {
        UUID orgId = user.getOrganisationId();
        UUID userId = user.getId();
        String role = user.getUtilisateur() != null && user.getUtilisateur().getRole() != null ? user.getUtilisateur().getRole().name() : "";
        return ResponseEntity.ok(ApiResponse.ok(todoDashboardService.getTodoDashboard(orgId, role, userId)));
    }

    @PostMapping("/action/{itemType}/{itemId}")
    @PreAuthorize("isAuthenticated()")
    @CacheEvict(value = "todo-dashboard", allEntries = true)
    public ResponseEntity<ApiResponse<QuickActionResponse>> action(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String itemType,
            @PathVariable UUID itemId,
            @Valid @RequestBody QuickActionRequest req) {
        UUID orgId = user.getOrganisationId();
        UUID userId = user.getId();
        QuickActionResponse out = todoDashboardService.executerActionRapide(itemId, itemType, req, orgId, userId);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/comptages")
    @PreAuthorize("isAuthenticated()")
    @Cacheable(value = "todo-dashboard-counts", key = "#user.organisationId.toString() + '_' + (#user.utilisateur != null && #user.utilisateur.role != null ? #user.utilisateur.role.name() : '')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> comptages(
            @AuthenticationPrincipal CustomUserDetails user) {
        UUID orgId = user.getOrganisationId();
        UUID userId = user.getId();
        String role = user.getUtilisateur() != null && user.getUtilisateur().getRole() != null ? user.getUtilisateur().getRole().name() : "";
        return ResponseEntity.ok(ApiResponse.ok(todoDashboardService.getComptages(orgId, role, userId)));
    }
}


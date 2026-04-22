package com.app.modules.auth.controller;

import com.app.audit.dto.AuditLogResponse;
import com.app.modules.auth.dto.AdminUserCreateRequest;
import com.app.modules.auth.dto.AdminUserResponse;
import com.app.modules.auth.dto.AdminUserUpdateRequest;
import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.auth.service.AdminService;
import com.app.shared.dto.ApiResponse;
import com.app.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> listUsers(
            @AuthenticationPrincipal CustomUserDetails user, @PageableDefault(size = 20) Pageable pageable) {
        Page<AdminUserResponse> page = adminService.listUsers(user.getOrganisationId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, r -> r)));
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> createUser(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody AdminUserCreateRequest req) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminService.createUser(req, user.getOrganisationId(), user.getId())));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUser(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody AdminUserUpdateRequest req) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminService.updateUser(id, req, user.getOrganisationId(), user.getId())));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> listAuditLogs(
            @AuthenticationPrincipal CustomUserDetails user, @PageableDefault(size = 25) Pageable pageable) {
        Page<AuditLogResponse> page = adminService.listAuditLogs(user.getOrganisationId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, r -> r)));
    }
}

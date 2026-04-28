package com.app.modules.templates.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.templates.dto.CreateTemplateRequest;
import com.app.modules.templates.dto.TemplateDefinitionResponse;
import com.app.modules.templates.dto.TemplateRevisionResponse;
import com.app.modules.templates.dto.UpdateTemplateRequest;
import com.app.modules.templates.service.TemplateService;
import com.app.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/templates/admin")
@RequiredArgsConstructor
public class TemplateAdminController {

    private final TemplateService templateService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<List<TemplateDefinitionResponse>>> list(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.list(user.getOrganisationId())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<TemplateDefinitionResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CreateTemplateRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.create(req, user.getOrganisationId(), user.getId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<TemplateDefinitionResponse>> update(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTemplateRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.update(id, req, user.getOrganisationId(), user.getId())));
    }

    @GetMapping("/{id}/revisions")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<List<TemplateRevisionResponse>>> revisions(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.listRevisions(id, user.getOrganisationId())));
    }

    @PostMapping(value = "/{id}/revisions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<TemplateRevisionResponse>> addRevision(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestPart(name = "comment", required = false) String comment
    ) throws Exception {
        return ResponseEntity.ok(ApiResponse.ok(templateService.addRevision(id, file, comment, user.getOrganisationId(), user.getId())));
    }
}


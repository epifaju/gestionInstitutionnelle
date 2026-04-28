package com.app.modules.templates.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.templates.dto.GenerateTemplateRequest;
import com.app.modules.templates.dto.GeneratedDocumentResponse;
import com.app.modules.templates.service.TemplateService;
import com.app.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateGenerateController {

    private final TemplateService templateService;

    @PostMapping(value = "/{code}/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<GeneratedDocumentResponse>> generate(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String code,
            @Valid @RequestBody GenerateTemplateRequest req
    ) throws Exception {
        boolean save = req.saveToGed() == null || req.saveToGed();
        return ResponseEntity.ok(ApiResponse.ok(templateService.generate(
                code,
                req.subjectType(),
                req.subjectId(),
                req.outputFormat(),
                save,
                req.overrides(),
                user.getOrganisationId(),
                user.getId()
        )));
    }

    @GetMapping("/generated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<GeneratedDocumentResponse>>> listGenerated(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam String subjectType,
            @RequestParam UUID subjectId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.listGenerated(user.getOrganisationId(), subjectType, subjectId)));
    }
}


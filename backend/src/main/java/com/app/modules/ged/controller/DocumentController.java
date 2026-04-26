package com.app.modules.ged.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.ged.dto.DocumentResponse;
import com.app.modules.ged.dto.DocumentShareRequest;
import com.app.modules.ged.dto.DocumentUpdateRequest;
import com.app.modules.ged.dto.DocumentUploadRequest;
import com.app.modules.ged.service.DocumentService;
import com.app.shared.dto.ApiResponse;
import com.app.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<DocumentResponse>>> search(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String[] tags,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) Boolean expirantBientot,
            @PageableDefault(size = 20) Pageable p) {
        Page<DocumentResponse> page =
                documentService.search(user.getOrganisationId(), query, type, tags, service, expirantBientot, p);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, r -> r)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestPart("document") DocumentUploadRequest req,
            @RequestPart("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(
                ApiResponse.ok(documentService.upload(req, file, user.getOrganisationId(), user.getId())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DocumentResponse>> getById(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.getById(id, user.getOrganisationId(), user.getId())));
    }

    @GetMapping("/{id}/url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPresignedUrl(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id) throws Exception {
        String url = documentService.generatePresignedUrl(id, user.getOrganisationId(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DocumentResponse>> modifier(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody DocumentUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.modifier(id, req, user.getOrganisationId(), user.getId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN') or @docSecurity.canDelete(#id,authentication)")
    public ResponseEntity<ApiResponse<Object>> supprimer(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id) {
        documentService.supprimer(id, user.getOrganisationId(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok("OK", null));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> versions(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.getVersions(id, user.getOrganisationId(), user.getId())));
    }

    @PostMapping("/{id}/partager")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> partager(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody DocumentShareRequest req) {
        documentService.partagerAvec(id, req.utilisateurId(), req.peutModifier(), req.peutSupprimer(), user.getOrganisationId());
        return ResponseEntity.ok(ApiResponse.ok("OK", null));
    }

    @GetMapping("/expiration-prochaine")
    @PreAuthorize("hasAnyRole('RH','ADMIN')")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> expirationProchaine(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "30") int nbJours) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.getDocumentsExpirantBientot(user.getOrganisationId(), nbJours)));
    }
}


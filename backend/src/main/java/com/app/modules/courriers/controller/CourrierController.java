package com.app.modules.courriers.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.courriers.dto.CourrierDocumentRequest;
import com.app.modules.courriers.dto.CourrierRequest;
import com.app.modules.courriers.dto.CourrierResponse;
import com.app.modules.courriers.dto.CourrierStatutRequest;
import com.app.modules.courriers.service.CourrierService;
import com.app.shared.dto.ApiResponse;
import com.app.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courriers")
@RequiredArgsConstructor
public class CourrierController {

    private final CourrierService courrierService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RH','FINANCIER')")
    public ResponseEntity<ApiResponse<PageResponse<CourrierResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String sens,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            Pageable pageable) {
        Page<CourrierResponse> page =
                courrierService.list(user.getOrganisationId(), sens, statut, q, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, x -> x)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RH','FINANCIER')")
    public ResponseEntity<ApiResponse<CourrierResponse>> get(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(courrierService.getById(id, user.getOrganisationId())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<CourrierResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody CourrierRequest req) {
        return ResponseEntity.ok(
                ApiResponse.ok(courrierService.create(req, user.getOrganisationId(), user.getId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<CourrierResponse>> update(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody CourrierRequest req) {
        boolean isAdmin = isAdmin(user);
        return ResponseEntity.ok(
                ApiResponse.ok(courrierService.update(id, req, user.getOrganisationId(), isAdmin)));
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<CourrierResponse>> changerStatut(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody CourrierStatutRequest req) {
        boolean isAdmin = isAdmin(user);
        return ResponseEntity.ok(ApiResponse.ok(
                courrierService.changerStatut(id, req.statut(), user.getOrganisationId(), isAdmin)));
    }

    @PostMapping("/{id}/document")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<CourrierResponse>> rattacherDocument(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody CourrierDocumentRequest req) {
        boolean isAdmin = isAdmin(user);
        return ResponseEntity.ok(ApiResponse.ok(
                courrierService.rattacherDocument(id, req.documentId(), user.getOrganisationId(), isAdmin)));
    }

    private static boolean isAdmin(CustomUserDetails user) {
        return user.getUtilisateur() != null
                && user.getUtilisateur().getRole() != null
                && "ADMIN".equals(user.getUtilisateur().getRole().name());
    }
}

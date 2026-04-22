package com.app.modules.inventaire.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.inventaire.dto.BienRequest;
import com.app.modules.inventaire.dto.BienResponse;
import com.app.modules.inventaire.dto.MouvementResponse;
import com.app.modules.inventaire.dto.ReformeRequest;
import com.app.modules.inventaire.dto.StatsInventaireResponse;
import com.app.modules.inventaire.service.InventaireService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventaire")
@RequiredArgsConstructor
public class InventaireController {

    private final InventaireService inventaireService;

    @GetMapping("/biens")
    @PreAuthorize("hasAnyRole('LOGISTIQUE','ADMIN','FINANCIER')")
    public ResponseEntity<ApiResponse<PageResponse<BienResponse>>> listBiens(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false) String etat,
            @RequestParam(required = false) String localisation,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<BienResponse> page =
                inventaireService.list(user.getOrganisationId(), categorie, etat, localisation, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page, r -> r)));
    }

    @PostMapping("/biens")
    @PreAuthorize("hasAnyRole('LOGISTIQUE','ADMIN')")
    public ResponseEntity<ApiResponse<BienResponse>> creerBien(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody BienRequest req) {
        return ResponseEntity.ok(
                ApiResponse.ok(inventaireService.creer(req, user.getOrganisationId(), user.getId())));
    }

    @GetMapping("/biens/{id}")
    @PreAuthorize("hasAnyRole('LOGISTIQUE','ADMIN')")
    public ResponseEntity<ApiResponse<BienResponse>> getBien(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(inventaireService.getById(id, user.getOrganisationId())));
    }

    @PutMapping("/biens/{id}")
    @PreAuthorize("hasAnyRole('LOGISTIQUE','ADMIN')")
    public ResponseEntity<ApiResponse<BienResponse>> modifierBien(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody BienRequest req) {
        return ResponseEntity.ok(
                ApiResponse.ok(inventaireService.modifier(id, req, user.getOrganisationId(), user.getId())));
    }

    @PostMapping("/biens/{id}/reforme")
    @PreAuthorize("hasAnyRole('LOGISTIQUE','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> reforme(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody ReformeRequest req) {
        inventaireService.reformer(id, req.motif(), user.getOrganisationId(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/biens/{id}/historique")
    @PreAuthorize("hasAnyRole('LOGISTIQUE','ADMIN')")
    public ResponseEntity<ApiResponse<List<MouvementResponse>>> historique(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.ok(inventaireService.getHistorique(id, user.getOrganisationId())));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('LOGISTIQUE','ADMIN','FINANCIER')")
    public ResponseEntity<ApiResponse<StatsInventaireResponse>> stats(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(inventaireService.getStats(user.getOrganisationId())));
    }
}

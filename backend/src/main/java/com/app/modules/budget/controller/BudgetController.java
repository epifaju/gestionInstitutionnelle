package com.app.modules.budget.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.budget.dto.BudgetRequest;
import com.app.modules.budget.dto.BudgetResponse;
import com.app.modules.budget.dto.ModifierLigneBudgetRequest;
import com.app.modules.budget.service.BudgetService;
import com.app.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping("/{annee}")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN','RH')")
    public ResponseEntity<ApiResponse<BudgetResponse>> getBudget(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable int annee) {
        return ResponseEntity.ok(ApiResponse.ok(budgetService.getBudget(user.getOrganisationId(), annee)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<BudgetResponse>> creer(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody BudgetRequest req) {
        return ResponseEntity.ok(
                ApiResponse.ok(budgetService.creer(req, user.getOrganisationId(), user.getId())));
    }

    @PutMapping("/{id}/ligne/{ligneId}")
    @PreAuthorize("hasAnyRole('FINANCIER','ADMIN')")
    public ResponseEntity<ApiResponse<BudgetResponse>> modifierLigne(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @PathVariable UUID ligneId,
            @Valid @RequestBody ModifierLigneBudgetRequest req) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        budgetService.modifierLigne(id, ligneId, req, user.getOrganisationId(), user.getId())));
    }

    @PostMapping("/{id}/valider")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BudgetResponse>> valider(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.ok(budgetService.valider(id, user.getOrganisationId(), user.getId())));
    }
}

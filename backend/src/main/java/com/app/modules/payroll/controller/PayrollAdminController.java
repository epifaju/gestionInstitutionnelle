package com.app.modules.payroll.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.payroll.dto.EmployeePayrollProfileRequest;
import com.app.modules.payroll.dto.EmployeePayrollProfileResponse;
import com.app.modules.payroll.dto.PayrollCotisationRequest;
import com.app.modules.payroll.dto.PayrollCotisationResponse;
import com.app.modules.payroll.dto.PayrollEmployerSettingsRequest;
import com.app.modules.payroll.dto.PayrollEmployerSettingsResponse;
import com.app.modules.payroll.dto.PayrollLegalConstantRequest;
import com.app.modules.payroll.dto.PayrollLegalConstantResponse;
import com.app.modules.payroll.dto.PayrollRubriqueRequest;
import com.app.modules.payroll.dto.PayrollRubriqueResponse;
import com.app.modules.payroll.service.PayrollAdminService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/payroll")
@RequiredArgsConstructor
public class PayrollAdminController {

    private final PayrollAdminService payrollAdminService;

    @GetMapping("/employer")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<PayrollEmployerSettingsResponse>> getEmployer(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(payrollAdminService.getEmployerSettings(user.getOrganisationId())));
    }

    @PutMapping("/employer")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<PayrollEmployerSettingsResponse>> upsertEmployer(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody PayrollEmployerSettingsRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(payrollAdminService.upsertEmployerSettings(user.getOrganisationId(), user.getId(), req)));
    }

    @GetMapping("/constants")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<List<PayrollLegalConstantResponse>>> listConstants(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(payrollAdminService.listLegalConstants(user.getOrganisationId())));
    }

    @PostMapping("/constants")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<PayrollLegalConstantResponse>> createConstant(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody PayrollLegalConstantRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(payrollAdminService.createLegalConstant(user.getOrganisationId(), user.getId(), req)));
    }

    @DeleteMapping("/constants/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<Void>> deleteConstant(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        payrollAdminService.deleteLegalConstant(id, user.getOrganisationId(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/rubriques")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<List<PayrollRubriqueResponse>>> listRubriques(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(payrollAdminService.listRubriques(user.getOrganisationId())));
    }

    @PostMapping("/rubriques")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<PayrollRubriqueResponse>> createRubrique(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody PayrollRubriqueRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(payrollAdminService.createRubrique(user.getOrganisationId(), user.getId(), req)));
    }

    @PutMapping("/rubriques/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<PayrollRubriqueResponse>> updateRubrique(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id, @Valid @RequestBody PayrollRubriqueRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(payrollAdminService.updateRubrique(id, user.getOrganisationId(), user.getId(), req)));
    }

    @DeleteMapping("/rubriques/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<Void>> deleteRubrique(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        payrollAdminService.deleteRubrique(id, user.getOrganisationId(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/cotisations")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<List<PayrollCotisationResponse>>> listCotisations(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(payrollAdminService.listCotisations(user.getOrganisationId())));
    }

    @PostMapping("/cotisations")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<PayrollCotisationResponse>> createCotisation(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody PayrollCotisationRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(payrollAdminService.createCotisation(user.getOrganisationId(), user.getId(), req)));
    }

    @PutMapping("/cotisations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<PayrollCotisationResponse>> updateCotisation(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id, @Valid @RequestBody PayrollCotisationRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(payrollAdminService.updateCotisation(id, user.getOrganisationId(), user.getId(), req)));
    }

    @DeleteMapping("/cotisations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<Void>> deleteCotisation(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        payrollAdminService.deleteCotisation(id, user.getOrganisationId(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/profiles")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<List<EmployeePayrollProfileResponse>>> listProfiles(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(payrollAdminService.listEmployeeProfiles(user.getOrganisationId())));
    }

    @PutMapping("/profiles")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<EmployeePayrollProfileResponse>> upsertProfile(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody EmployeePayrollProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(payrollAdminService.upsertEmployeeProfile(user.getOrganisationId(), user.getId(), req)));
    }

    @DeleteMapping("/profiles/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RH')")
    public ResponseEntity<ApiResponse<Void>> deleteProfile(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        payrollAdminService.deleteEmployeeProfile(id, user.getOrganisationId(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}


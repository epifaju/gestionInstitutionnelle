package com.app.modules.auth.controller;

import com.app.modules.auth.dto.ForgotPasswordRequest;
import com.app.modules.auth.dto.LoginRequest;
import com.app.modules.auth.dto.LoginResponse;
import com.app.modules.auth.dto.MessageResponse;
import com.app.modules.auth.dto.RefreshResponse;
import com.app.modules.auth.dto.ResetPasswordRequest;
import com.app.modules.auth.dto.UpdateLangueRequest;
import com.app.modules.auth.dto.UserInfo;
import com.app.modules.auth.service.AuthService;
import com.app.shared.dto.ApiResponse;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @PermitAll
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request, response)));
    }

    @PostMapping("/forgot-password")
    @PermitAll
    public ResponseEntity<ApiResponse<MessageResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.forgotPassword(request)));
    }

    @PostMapping("/reset-password")
    @PermitAll
    public ResponseEntity<ApiResponse<MessageResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.resetPassword(request)));
    }

    @PostMapping("/refresh")
    @PermitAll
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request, response)));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Object>> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(ApiResponse.ok("Déconnecté", null));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserInfo>> me() {
        return ResponseEntity.ok(ApiResponse.ok(authService.getMe()));
    }

    @PutMapping("/me/langue")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserInfo>> updateLangue(@Valid @RequestBody UpdateLangueRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.updateLangue(req.langue())));
    }
}

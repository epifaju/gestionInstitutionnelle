package com.app.modules.notifications.controller;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.notifications.dto.NotificationResponse;
import com.app.modules.notifications.service.NotificationService;
import com.app.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> mesNotifications(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(name = "nonLuesSeulement", defaultValue = "false") boolean nonLuesSeulement,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                notificationService.getMesNotifications(user.getId(), nonLuesSeulement, pageable)
        ));
    }

    @PutMapping("/{id}/lu")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> marquerLu(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id
    ) {
        notificationService.marquerLu(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/tout-lu")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> marquerToutLu(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        notificationService.marquerToutLu(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/nb-non-lues")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Long>> compterNonLues(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.compterNonLues(user.getId())));
    }
}


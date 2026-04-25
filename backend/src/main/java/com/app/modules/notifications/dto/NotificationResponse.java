package com.app.modules.notifications.dto;

import com.app.modules.notifications.entity.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String titre,
        String message,
        String lien,
        boolean lu,
        LocalDateTime createdAt
) {
}


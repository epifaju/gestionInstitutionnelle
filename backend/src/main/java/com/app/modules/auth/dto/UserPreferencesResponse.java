package com.app.modules.auth.dto;

import java.util.List;

public record UserPreferencesResponse(
        String theme,
        List<String> notificationsUiEnabled,
        List<String> notificationsEmailEnabled
) {}


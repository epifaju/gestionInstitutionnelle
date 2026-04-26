package com.app.modules.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UserPreferencesRequest(
        @NotNull String theme,
        @NotNull List<String> notificationsUiEnabled,
        @NotNull List<String> notificationsEmailEnabled
) {}


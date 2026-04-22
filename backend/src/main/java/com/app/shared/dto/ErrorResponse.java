package com.app.shared.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(status, code, message, null, LocalDateTime.now());
    }

    public static ErrorResponse of(int status, String code, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(status, code, message, fieldErrors, LocalDateTime.now());
    }
}

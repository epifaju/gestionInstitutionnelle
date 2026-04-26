package com.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.ged")
public class GedProperties {
    /**
     * Maximum file size (bytes) accepted by the GED module.
     */
    private long maxFileBytes = 52_428_800L; // 50 MiB

    /**
     * Presigned URL expiry (seconds).
     */
    private int presignExpirySeconds = 3600;

    /**
     * Allowed MIME types for uploads.
     * Default list is aligned with the UI: PDF, images, DOCX, XLSX.
     */
    private List<String> allowedMimeTypes = List.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
}


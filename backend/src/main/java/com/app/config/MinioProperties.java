package com.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {
    private String endpoint = "http://localhost:9000";
    /**
     * Base URL used in links returned to the browser (e.g. presigned GET URLs).
     * In Docker, the internal endpoint is typically http://minio:9000 (reachable from backend),
     * but the browser must use a host-reachable URL (e.g. http://localhost:9000).
     */
    private String publicEndpoint;
    private String accessKey = "minio";
    private String secretKey = "minio12345";
    private String bucket = "gestion";
}

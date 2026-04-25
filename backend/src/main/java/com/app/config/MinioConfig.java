package com.app.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean(name = "internalMinioClient")
    public MinioClient internalMinioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .region(properties.getRegion())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    /**
     * Client used only to generate presigned URLs that will be opened by the browser.
     * Presigned URLs are signed with the request host; therefore the endpoint MUST match the public URL.
     */
    @Bean(name = "publicMinioClient")
    public MinioClient publicMinioClient(MinioProperties properties) {
        String endpoint = properties.getPublicEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = properties.getEndpoint();
        }
        return MinioClient.builder()
                .endpoint(endpoint)
                .region(properties.getRegion())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}

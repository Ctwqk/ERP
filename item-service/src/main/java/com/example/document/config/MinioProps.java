package com.example.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param endpoint       address this service uses to reach MinIO (e.g. {@code http://minio:9000} inside Docker)
 * @param publicEndpoint address clients use for presigned URLs; defaults to {@code endpoint} when blank
 * @param region         bucket region used for signing, so no extra round trip to MinIO is needed
 */
@ConfigurationProperties(prefix = "minio")
public record MinioProps(String endpoint, String publicEndpoint, String accessKey, String secretKey, String bucket,
        String region) {

    public String effectivePublicEndpoint() {
        return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    }
}

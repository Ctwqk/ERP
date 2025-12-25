package com.example.document.dto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
public record MinioProps(String endpoint, String publicEndpoint, String accessKey, String secretKey, String bucket) {
}
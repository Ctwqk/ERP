package com.example.document.config;

import com.example.document.dto.MinioProps;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {
    @Bean
    MinioClient minioClient(MinioProps p) {
        return MinioClient.builder()
                .endpoint(p.endpoint())
                .credentials(p.accessKey(), p.secretKey())
                .build();
    }
}

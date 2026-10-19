package com.example.document.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    /** Talks to MinIO over the internal network (stat, download, bucket management). */
    @Bean
    @Qualifier("minioClient")
    MinioClient minioClient(MinioProps p) {
        return MinioClient.builder()
                .endpoint(p.endpoint())
                .credentials(p.accessKey(), p.secretKey())
                .region(p.region())
                .build();
    }

    /**
     * Only used to <em>sign</em> URLs. SigV4 signatures cover the Host header, so a URL meant for clients outside the
     * Docker network must be signed for the public address - rewriting the host afterwards would invalidate it.
     * Signing is a local computation, so this client never connects to the public endpoint.
     */
    @Bean
    @Qualifier("presignClient")
    MinioClient presignClient(MinioProps p) {
        return MinioClient.builder()
                .endpoint(p.effectivePublicEndpoint())
                .credentials(p.accessKey(), p.secretKey())
                .region(p.region())
                .build();
    }
}

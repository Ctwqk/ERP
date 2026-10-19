package com.example.item;

import com.example.order.integration.InventoryClient;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Throwaway PostgreSQL, RabbitMQ and MinIO for integration tests, shared by every IT class (one Spring context, one
 * set of containers per test run), plus a controllable stand-in for inventory-service.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    /** The same digest-pinned MinIO build as the local Compose stack. */
    static final String MINIO_IMAGE = "cgr.dev/chainguard/minio@sha256:2915cb406f38ed6ff9634de0a3e4a9423e2dda06a6f1aca18f8c968aa37a01f6";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    private static final MinIOContainer MINIO = new MinIOContainer(
            DockerImageName.parse(MINIO_IMAGE).asCompatibleSubstituteFor("minio/minio"));

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return POSTGRES;
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return RABBIT;
    }

    @Bean
    MinIOContainer minio() {
        return MINIO;
    }

    @Bean
    DynamicPropertyRegistrar minioProperties(MinIOContainer minio) {
        return registry -> {
            registry.add("minio.endpoint", minio::getS3URL);
            registry.add("minio.public-endpoint", minio::getS3URL);
            registry.add("minio.access-key", minio::getUserName);
            registry.add("minio.secret-key", minio::getPassword);
            registry.add("minio.bucket", () -> "it-assets");
        };
    }

    /** In-memory inventory: tests set the stock levels they need. */
    public static class FakeInventory implements InventoryClient {
        private final Map<UUID, BigDecimal> stock = new ConcurrentHashMap<>();

        public void set(UUID itemId, String quantity) {
            stock.put(itemId, new BigDecimal(quantity));
        }

        @Override
        public BigDecimal availableQuantity(UUID itemId) {
            return stock.getOrDefault(itemId, BigDecimal.ZERO);
        }
    }

    @Bean
    @Primary
    FakeInventory fakeInventory() {
        return new FakeInventory();
    }
}

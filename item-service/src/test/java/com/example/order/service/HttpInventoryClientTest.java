package com.example.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.order.integration.HttpInventoryClient;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class HttpInventoryClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> seenAuth = new AtomicReference<>();
    private final UUID stocked = UUID.randomUUID();
    private final UUID broken = UUID.randomUUID();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/inventory/stocks/", exchange -> {
            seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String path = exchange.getRequestURI().getPath();
            int status = 404;
            byte[] body = new byte[0];
            if (path.endsWith(stocked.toString())) {
                status = 200;
                body = ("{\"id\":\"" + UUID.randomUUID() + "\",\"quantity\":7.25,\"itemId\":\"" + stocked + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
            } else if (path.endsWith(broken.toString())) {
                status = 500;
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        Jwt jwt = Jwt.withTokenValue("caller-token").header("alg", "none").subject("a@b.c")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void stop() {
        server.stop(0);
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsQuantityAndForwardsTheCallersToken() {
        HttpInventoryClient client = new HttpInventoryClient(RestClient.builder(), baseUrl);

        assertThat(client.availableQuantity(stocked)).isEqualByComparingTo(new BigDecimal("7.25"));
        assertThat(seenAuth.get()).isEqualTo("Bearer caller-token");
    }

    @Test
    void neverStockedItemHasZeroAvailable() {
        assertThat(new HttpInventoryClient(RestClient.builder(), baseUrl).availableQuantity(UUID.randomUUID()))
                .isEqualByComparingTo("0");
    }

    @Test
    void upstreamFailuresMapToGatewayErrors() {
        HttpInventoryClient client = new HttpInventoryClient(RestClient.builder(), baseUrl);
        assertThatThrownBy(() -> client.availableQuantity(broken))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));

        server.stop(0);
        assertThatThrownBy(() -> client.availableQuantity(stocked))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}

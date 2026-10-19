package com.example.task.ports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.task.ports.impl.DownstreamHttp;
import com.example.task.ports.impl.HttpCorePort;
import com.example.task.ports.impl.HttpInventoryPort;
import com.example.task.ports.impl.HttpUserPort;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** The HTTP ports against a tiny in-process server standing in for the other services. */
class HttpPortsTest {

    private HttpServer server;
    private String baseUrl;
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private final UUID knownUser = UUID.randomUUID();
    private final UUID stockedItem = UUID.randomUUID();
    private DownstreamHttp http;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            String path = exchange.getRequestURI().getPath();
            int status = 404;
            String body = "";
            if (path.equals("/api/users/" + knownUser) || path.equals("/api/items/" + stockedItem)) {
                status = 200;
                body = "{}";
            } else if (path.equals("/api/inventory/stocks/" + stockedItem)) {
                status = 200;
                body = "{\"id\":\"" + UUID.randomUUID() + "\",\"quantity\":12.5,\"extra\":true}";
            } else if (path.startsWith("/api/orders/")) {
                status = 403;
            } else if (path.startsWith("/boom/")) {
                status = 500;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        http = new DownstreamHttp(RestClient.builder());

        Jwt jwt = Jwt.withTokenValue("the-caller-token").header("alg", "none").subject("a@b.c")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void stop() {
        server.stop(0);
        SecurityContextHolder.clearContext();
    }

    @Test
    void userLookupForwardsCallerTokenAndMapsStatuses() {
        HttpUserPort port = new HttpUserPort(http, baseUrl);

        assertThat(port.existsById(knownUser)).isTrue();
        assertThat(port.existsById(UUID.randomUUID())).isFalse();
        assertThat(authHeaders).allMatch("Bearer the-caller-token"::equals);
    }

    @Test
    void itemAndDocumentLookups() {
        HttpCorePort port = new HttpCorePort(http, baseUrl);

        assertThat(port.existsItem(stockedItem)).isTrue();
        assertThat(port.existsItem(UUID.randomUUID())).isFalse();
        assertThat(port.existsDocument(UUID.randomUUID())).isFalse();
    }

    @Test
    void unexpectedStatusIsReportedAsBadGateway() {
        HttpCorePort port = new HttpCorePort(http, baseUrl);

        assertThatThrownBy(() -> port.existsOrder(UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void stockAvailabilityComparesAgainstReportedQuantity() {
        HttpInventoryPort port = new HttpInventoryPort(http, baseUrl);

        assertThat(port.checkStockAvailable(stockedItem, new BigDecimal("12.5"))).isTrue();
        assertThat(port.checkStockAvailable(stockedItem, new BigDecimal("12.500001"))).isFalse();
        assertThat(port.checkStockAvailable(UUID.randomUUID(), BigDecimal.ONE)).isFalse();
    }

    @Test
    void unreachableServiceIsServiceUnavailable() {
        server.stop(0);
        HttpUserPort port = new HttpUserPort(http, baseUrl);

        assertThatThrownBy(() -> port.existsById(knownUser))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}

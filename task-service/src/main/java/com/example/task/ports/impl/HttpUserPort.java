package com.example.task.ports.impl;

import com.example.task.ports.UserPort;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Resolves users against user-service ({@code GET /api/users/{id}}). */
@Component
@ConditionalOnProperty(name = "erp.ports.mode", havingValue = "http", matchIfMissing = true)
public class HttpUserPort implements UserPort {

    private final DownstreamHttp http;
    private final RestClient client;

    public HttpUserPort(DownstreamHttp http, @Value("${erp.ports.user-service-url}") String baseUrl) {
        this.http = http;
        this.client = http.clientFor(baseUrl);
    }

    @Override
    public boolean existsById(UUID userId) {
        return http.lookup(client, "user-service", "/api/users/{id}", userId) == DownstreamHttp.Lookup.FOUND;
    }
}

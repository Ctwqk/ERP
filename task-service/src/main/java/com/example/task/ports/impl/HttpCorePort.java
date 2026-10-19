package com.example.task.ports.impl;

import com.example.task.ports.CorePort;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Resolves items, orders and documents against item-service. */
@Component
@ConditionalOnProperty(name = "erp.ports.mode", havingValue = "http", matchIfMissing = true)
public class HttpCorePort implements CorePort {

    private final DownstreamHttp http;
    private final RestClient client;

    public HttpCorePort(DownstreamHttp http, @Value("${erp.ports.item-service-url}") String baseUrl) {
        this.http = http;
        this.client = http.clientFor(baseUrl);
    }

    @Override
    public boolean existsOrder(UUID orderId) {
        return http.lookup(client, "item-service", "/api/orders/{id}", orderId) == DownstreamHttp.Lookup.FOUND;
    }

    @Override
    public boolean existsItem(UUID itemId) {
        return http.lookup(client, "item-service", "/api/items/{id}", itemId) == DownstreamHttp.Lookup.FOUND;
    }

    @Override
    public boolean existsDocument(UUID documentId) {
        return http.lookup(client, "item-service", "/api/documents/{id}", documentId)
                == DownstreamHttp.Lookup.FOUND;
    }
}

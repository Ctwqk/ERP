package com.example.order.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.example.order.dto.InventoryStockResponse;

import java.util.UUID;

public class HttpInventoryClient implements InventoryClient {

    private final RestClient restClient;

    public HttpInventoryClient(@Value("${order.inventory-service.base-url:http://localhost:8083}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();

    }

    @Override
    public InventoryStockResponse getStock(UUID itemId) {
        try {
            return restClient.get().uri("/api/inventory/stocks/{itemId}", itemId).retrieve()
                    .body(InventoryStockResponse.class);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found for item " + itemId, ex);
        }
    }
}

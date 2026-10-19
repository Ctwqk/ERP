package com.example.task.ports.impl;

import com.example.task.ports.InventoryPort;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Reads stock levels from inventory-service ({@code GET /api/inventory/stocks/{itemId}}). */
@Component
@ConditionalOnProperty(name = "erp.ports.mode", havingValue = "http", matchIfMissing = true)
public class HttpInventoryPort implements InventoryPort {

    record StockResponse(BigDecimal quantity) {
    }

    private final RestClient client;

    public HttpInventoryPort(DownstreamHttp http, @Value("${erp.ports.inventory-service-url}") String baseUrl) {
        this.client = http.clientFor(baseUrl);
    }

    @Override
    public boolean checkStockAvailable(UUID itemId, BigDecimal quantity) {
        try {
            StockResponse stock = client.get().uri("/api/inventory/stocks/{id}", itemId)
                    .exchange((req, res) -> {
                        if (res.getStatusCode().value() == 404) {
                            return null; // never stocked => nothing available
                        }
                        if (!res.getStatusCode().is2xxSuccessful()) {
                            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                    "inventory-service answered " + res.getStatusCode().value());
                        }
                        return res.bodyTo(StockResponse.class);
                    });
            return stock != null && stock.quantity() != null && stock.quantity().compareTo(quantity) >= 0;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "inventory-service is unavailable", ex);
        }
    }
}

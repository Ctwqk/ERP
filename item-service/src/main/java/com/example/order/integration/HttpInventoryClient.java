package com.example.order.integration;

import com.example.common.http.ForwardedTokenInterceptor;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Calls inventory-service with the caller's own access token. */
@Component
public class HttpInventoryClient implements InventoryClient {

    record StockResponse(BigDecimal quantity) {
    }

    private final RestClient client;

    public HttpInventoryClient(RestClient.Builder builder, @Value("${erp.inventory-service.url}") String baseUrl) {
        this.client = builder.clone().baseUrl(baseUrl).requestInterceptor(new ForwardedTokenInterceptor()).build();
    }

    @Override
    public BigDecimal availableQuantity(UUID itemId) {
        try {
            StockResponse stock = client.get().uri("/api/inventory/stocks/{id}", itemId).exchange((req, res) -> {
                if (res.getStatusCode().value() == 404) {
                    return null;
                }
                if (!res.getStatusCode().is2xxSuccessful()) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "inventory-service answered " + res.getStatusCode().value());
                }
                return res.bodyTo(StockResponse.class);
            });
            return stock == null || stock.quantity() == null ? BigDecimal.ZERO : stock.quantity();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "inventory-service is unavailable", ex);
        }
    }
}

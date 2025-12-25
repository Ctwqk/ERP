package com.example.order.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpItemLookupClient implements ItemLookupClient {

    private final RestClient restClient;

    public HttpItemLookupClient(@Value("${order.item-service.base-url:http://localhost:8082}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public void assertItemsExist(List<ItemReference> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must contain at least one item");
        }
        for (ItemReference ref : items) {
            if (ref == null || (!hasText(ref.sku()) && ref.itemId() == null)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each item requires itemId or sku");
            }
            validateSingle(ref);
        }
    }

    private void validateSingle(ItemReference ref) {
        try {
            if (ref.itemId() != null) {
                restClient.get()
                        .uri("/api/items/{id}", ref.itemId())
                        .retrieve()
                        .toBodilessEntity();
                return;
            }
            restClient.get()
                    .uri("/api/items/sku/{sku}", ref.sku())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Item not found: %s".formatted(refLabel(ref)), ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String refLabel(ItemReference ref) {
        UUID id = ref.itemId();
        if (id != null) {
            return id.toString();
        }
        return Objects.requireNonNullElse(ref.sku(), "unknown");
    }
}



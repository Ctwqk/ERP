package com.example.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OrderItemRequest(
        UUID itemId,
        String sku,
        @NotNull @Min(1) Integer quantity,
        @NotNull @Min(0) Long unitPriceCents) {

    public boolean hasItemReference() {
        return itemId != null || (sku != null && !sku.isBlank());
    }
}



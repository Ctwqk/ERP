package com.example.order.dto;

import com.example.order.domain.OrderItem;
import java.util.UUID;

public record OrderItemDto(
        UUID id,
        UUID itemId,
        String sku,
        Integer quantity,
        Long unitPriceCents,
        Long lineAmountCents) {

    public OrderItemDto(OrderItem entity) {
        this(entity.getId(),
                entity.getItemId(),
                entity.getSku(),
                entity.getQuantity(),
                entity.getUnitPriceCents(),
                entity.getLineAmountCents());
    }
}



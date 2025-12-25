package com.example.inventory.dto;

import com.example.inventory.domain.InventoryStock;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryStockDto(
        UUID id,
        UUID itemId,
        Double quantity,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public InventoryStockDto(InventoryStock stock) {
        this(stock.getId(), stock.getItemId(), stock.getQuantity(), stock.getCreatedAt(), stock.getUpdatedAt());
    }
}



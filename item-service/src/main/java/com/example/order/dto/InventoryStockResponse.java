package com.example.order.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryStockResponse(UUID id, UUID itemId, Double quentity, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    
}

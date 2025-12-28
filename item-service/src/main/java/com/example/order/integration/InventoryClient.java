package com.example.order.integration;

import java.util.UUID;

import com.example.order.dto.InventoryStockResponse;

public interface InventoryClient {
    InventoryStockResponse getStock(UUID itemId);
}

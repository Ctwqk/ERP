package com.example.task.ports.impl;

import com.example.task.ports.InventoryPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StubInventoryPort implements InventoryPort {
    @Override
    public boolean checkStockAvailable(UUID itemId, double quantity) {
        // TODO: replace with HTTP client to inventory-service
        return true;
    }
}


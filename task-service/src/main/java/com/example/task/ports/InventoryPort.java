package com.example.task.ports;

import java.util.UUID;

public interface InventoryPort {
    boolean checkStockAvailable(UUID itemId, double quantity);
}

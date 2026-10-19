package com.example.task.ports;

import java.math.BigDecimal;
import java.util.UUID;

/** Outbound port to inventory-service. */
public interface InventoryPort {
    /** @return true if at least {@code quantity} of the item is currently in stock */
    boolean checkStockAvailable(UUID itemId, BigDecimal quantity);
}

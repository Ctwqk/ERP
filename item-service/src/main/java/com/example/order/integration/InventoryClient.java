package com.example.order.integration;

import java.math.BigDecimal;
import java.util.UUID;

/** Outbound port to inventory-service. */
public interface InventoryClient {

    /** Quantity currently in stock; zero if the item has never been stocked. */
    BigDecimal availableQuantity(UUID itemId);
}

package com.example.task.ports.impl;

import com.example.task.ports.InventoryPort;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Always-available stock; only active with {@code erp.ports.mode=stub} (standalone development). */
@Component
@ConditionalOnProperty(name = "erp.ports.mode", havingValue = "stub")
public class StubInventoryPort implements InventoryPort {
    @Override
    public boolean checkStockAvailable(UUID itemId, BigDecimal quantity) {
        return true;
    }
}

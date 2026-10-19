package com.example.task.ports.impl;

import com.example.task.ports.CorePort;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Accepts every reference; only active with {@code erp.ports.mode=stub} (standalone development). */
@Component
@ConditionalOnProperty(name = "erp.ports.mode", havingValue = "stub")
public class StubCorePort implements CorePort {
    @Override
    public boolean existsOrder(UUID orderId) {
        return true;
    }

    @Override
    public boolean existsItem(UUID itemId) {
        return true;
    }

    @Override
    public boolean existsDocument(UUID documentId) {
        return true;
    }
}


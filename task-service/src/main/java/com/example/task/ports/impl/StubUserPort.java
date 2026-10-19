package com.example.task.ports.impl;

import com.example.task.ports.UserPort;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Accepts every reference; only active with {@code erp.ports.mode=stub} (standalone development). */
@Component
@ConditionalOnProperty(name = "erp.ports.mode", havingValue = "stub")
public class StubUserPort implements UserPort {
    @Override
    public boolean existsById(UUID userId) {
        return true;
    }
}


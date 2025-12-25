package com.example.task.ports.impl;

import com.example.task.ports.CorePort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
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


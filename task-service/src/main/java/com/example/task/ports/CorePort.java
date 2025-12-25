package com.example.task.ports;

import java.util.UUID;

public interface CorePort {
    boolean existsOrder(UUID orderId);
    boolean existsItem(UUID itemId);
    boolean existsDocument(UUID documentId);
}


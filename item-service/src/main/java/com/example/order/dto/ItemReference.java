package com.example.order.dto;

import java.util.UUID;

public record ItemReference(UUID itemId, String sku) {
}



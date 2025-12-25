package com.example.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotEmpty List<@Valid OrderItemRequest> items,
        List<UUID> documentIds) {
}



package com.example.order.dto;

import com.example.order.domain.Order;
import com.example.order.domain.Order.Status;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record OrderDto(
        UUID id,
        String userId,
        Status status,
        Long totalAmountCents,
        List<OrderItemDto> items,
        List<UUID> documentIds,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public OrderDto(Order order, List<UUID> documentIds) {
        this(order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmountCents(),
                order.getItems().stream().map(OrderItemDto::new).collect(Collectors.toList()),
                documentIds,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}



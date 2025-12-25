package com.example.order.dto;

import com.example.order.domain.Order.Status;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull Status status) {
}



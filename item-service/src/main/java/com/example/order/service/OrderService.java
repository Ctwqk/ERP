package com.example.order.service;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderDto;
import com.example.order.dto.UpdateOrderStatusRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderDto createOrder(CreateOrderRequest request);

    OrderDto getOrder(UUID id);

    Page<OrderDto> listMyOrders(Pageable pageable);

    OrderDto updateStatus(UUID id, UpdateOrderStatusRequest request);
}


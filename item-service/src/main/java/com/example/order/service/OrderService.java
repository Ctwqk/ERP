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

    /**
     * Moves an order along NEW -> CONFIRMED -> COMPLETED (or to CANCELED from NEW/CONFIRMED). Confirming checks
     * stock in inventory-service when {@code erp.orders.check-stock-on-confirm} is enabled.
     */
    OrderDto updateStatus(UUID id, UpdateOrderStatusRequest request);

    /** Creates an order for the current user from an uploaded .xlsx document (see {@code ReadXlsx} for columns). */
    OrderDto createOrderFromXlsx(UUID documentId);

    /** Same as {@link #createOrderFromXlsx(UUID)} for a given user, used when no security context exists (events). */
    OrderDto importOrderFromDocument(UUID documentId, String userId);
}

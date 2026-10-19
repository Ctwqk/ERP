package com.example.order.controller;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderDto;
import com.example.order.dto.UpdateOrderStatusRequest;
import com.example.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.InputStream;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Customer orders. Each user sees and changes only their own orders.")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/{id}")
    public OrderDto getById(@PathVariable UUID id) {
        return orderService.getOrder(id);
    }

    @GetMapping
    public Page<OrderDto> listMine(@RequestParam(name = "user", defaultValue = "me") String user,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        if (!"me".equalsIgnoreCase(user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only user=me is supported");
        }
        return orderService.listMyOrders(pageable);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change order status",
            description = "NEW -> CONFIRMED -> COMPLETED, or CANCELED from NEW/CONFIRMED. Confirming checks stock in "
                    + "inventory-service (409 if insufficient).")
    public OrderDto updateStatus(@PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request);
    }

    @PostMapping("/{documentId}/import")
    @Operation(summary = "Create an order from an uploaded .xlsx document",
            description = "Columns: item_id | sku_code | quantity | unit_price_cents | document_ids. Normally this "
                    + "happens automatically when an ORDER spreadsheet upload completes.")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto createOrderFromXlsx(@PathVariable UUID documentId) {
        return orderService.createOrderFromXlsx(documentId);
    }
}

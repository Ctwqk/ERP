package com.example.inventory.controller;

import com.example.common.security.JwtUsers;
import com.example.inventory.dto.InventoryStockDto;
import com.example.inventory.dto.InventoryTransactionDto;
import com.example.inventory.dto.TransactionRequest;
import com.example.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Stock levels and stock movements")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/stocks/{itemId}")
    @Operation(summary = "Current stock of an item", description = "404 if the item has never been stocked")
    public InventoryStockDto getStockByItem(@PathVariable UUID itemId) {
        return inventoryService.getStockByItemId(itemId);
    }

    @PostMapping("/stocks/{itemId}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a stock movement (IN or OUT) for an item",
            description = "IN creates the stock row on first use. OUT fails with 409 when it would go below zero.")
    public InventoryStockDto recordTransaction(@PathVariable UUID itemId, @Valid @RequestBody TransactionRequest req,
            Authentication authentication) {
        UUID userId = JwtUsers.userId(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token has no user id (uid claim)");
        }
        return inventoryService.recordTransaction(itemId, userId, req.type(), req.quantity());
    }

    @GetMapping("/stocks/{stockId}/transactions")
    @Operation(summary = "Movement history of a stock row, newest first")
    public List<InventoryTransactionDto> listTransactions(@PathVariable UUID stockId) {
        return inventoryService.listTransactions(stockId);
    }
}

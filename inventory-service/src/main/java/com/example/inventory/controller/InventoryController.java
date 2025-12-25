package com.example.inventory.controller;

import com.example.inventory.domain.InventoryTransaction.TransactionType;
import com.example.inventory.dto.InventoryStockDto;
import com.example.inventory.dto.InventoryTransactionDto;
import com.example.inventory.dto.TransactionRequest;
import com.example.inventory.service.InventoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/stocks/{itemId}")
    public InventoryStockDto getStockByItem(@PathVariable UUID itemId) {
        return inventoryService.getStockByItemId(itemId);
    }

    @PostMapping("/stocks/{itemId}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryStockDto recordTransaction(@PathVariable UUID itemId, @Valid @RequestBody TransactionRequest req) {
        return inventoryService.recordTransaction(itemId, req.userId(), req.type(), req.quantity());
    }

    @GetMapping("/stocks/{stockId}/transactions")
    public List<InventoryTransactionDto> listTransactions(@PathVariable UUID stockId) {
        return inventoryService.listTransactions(stockId);
    }
}


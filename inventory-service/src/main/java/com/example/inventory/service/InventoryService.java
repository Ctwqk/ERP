package com.example.inventory.service;

import com.example.inventory.domain.InventoryTransaction.TransactionType;
import com.example.inventory.dto.InventoryStockDto;
import com.example.inventory.dto.InventoryTransactionDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InventoryService {

    InventoryStockDto getStockByItemId(UUID itemId);

    /**
     * Applies a movement to the item's stock. {@code IN} creates the stock row on first use; {@code OUT} fails with
     * 404 if the item was never stocked and 409 if it would drive the quantity below zero.
     */
    InventoryStockDto recordTransaction(UUID itemId, UUID userId, TransactionType type, BigDecimal quantity);

    List<InventoryTransactionDto> listTransactions(UUID stockId);
}

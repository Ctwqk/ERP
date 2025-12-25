package com.example.inventory.service;

import com.example.inventory.domain.InventoryTransaction.TransactionType;
import com.example.inventory.dto.InventoryStockDto;
import com.example.inventory.dto.InventoryTransactionDto;
import java.util.List;
import java.util.UUID;

public interface InventoryService {

    InventoryStockDto getStockByItemId(UUID itemId);

    InventoryStockDto recordTransaction(UUID itemId, UUID userId, TransactionType type, double quantity);

    List<InventoryTransactionDto> listTransactions(UUID stockId);
}

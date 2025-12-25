package com.example.inventory.dto;

import com.example.inventory.domain.InventoryTransaction;
import com.example.inventory.domain.InventoryTransaction.TransactionType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryTransactionDto(
        UUID id,
        UUID stockId,
        UUID userId,
        TransactionType transactionType,
        Double quantity,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public InventoryTransactionDto(InventoryTransaction tx) {
        this(tx.getId(), tx.getStockId(), tx.getUserId(), tx.getTransactionType(), tx.getQuantity(),
                tx.getCreatedAt(), tx.getUpdatedAt());
    }
}



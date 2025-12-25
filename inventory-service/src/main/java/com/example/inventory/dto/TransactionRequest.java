package com.example.inventory.dto;

import com.example.inventory.domain.InventoryTransaction.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record TransactionRequest(
        @NotNull UUID userId,
        @NotNull TransactionType type,
        @Positive double quantity
) {
}


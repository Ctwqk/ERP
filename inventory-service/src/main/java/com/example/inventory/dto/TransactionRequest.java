package com.example.inventory.dto;

import com.example.inventory.domain.InventoryTransaction.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * A stock movement. The acting user is taken from the access token, not from the request body.
 *
 * @param type     {@code IN} adds stock, {@code OUT} removes it (never below zero)
 * @param quantity strictly positive, at most 6 decimal places
 */
public record TransactionRequest(
        @NotNull TransactionType type,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 6) BigDecimal quantity) {
}

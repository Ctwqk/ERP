package com.example.item.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Material needed to build a given quantity of the BOM's product.
 *
 * @param netQuantity   {@code qtyPer * quantity}: what ends up in the product
 * @param grossQuantity {@code netQuantity / (1 - scrapRate)}: what must be issued to cover expected scrap
 */
public record BomRequirementDto(
        int lineNo,
        UUID componentItemId,
        String componentSku,
        UUID uomId,
        BigDecimal netQuantity,
        BigDecimal scrapRate,
        BigDecimal grossQuantity) {
}

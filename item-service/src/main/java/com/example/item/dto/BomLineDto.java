package com.example.item.dto;

import com.example.item.domain.BomLine;
import java.math.BigDecimal;
import java.util.UUID;

public record BomLineDto(
        UUID id,
        Integer lineNo,
        UUID componentItemId,
        BigDecimal qtyPer,
        UUID uomId,
        BigDecimal scrapRate,
        String note
) {
    public BomLineDto(BomLine line) {
        this(line.getId(),
                line.getLineNo(),
                line.getComponentItem() != null ? line.getComponentItem().getId() : null,
                line.getQtyPer(),
                line.getUom() != null ? line.getUom().getId() : null,
                line.getScrapRate(),
                line.getNote());
    }
}


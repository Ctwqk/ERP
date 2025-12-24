package com.example.item.dto;

import com.example.item.domain.Uom;
import java.util.UUID;

public record UomDto(
        UUID id,
        String code,
        String name
) {
    public UomDto(Uom uom) {
        this(uom.getId(), uom.getCode(), uom.getName());
    }
}


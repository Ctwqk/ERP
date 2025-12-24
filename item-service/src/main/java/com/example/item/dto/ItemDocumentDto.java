package com.example.item.dto;

import com.example.item.domain.ItemDocument;
import com.example.item.domain.ItemDocument.RelationType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ItemDocumentDto(
        UUID itemId,
        UUID documentId,
        RelationType relationType,
        Boolean isCurrent,
        OffsetDateTime createdAt) {
    public ItemDocumentDto(ItemDocument link) {
        this(link.getItem() != null ? link.getItem().getId() : null,
                link.getDocument() != null ? link.getDocument().getId() : null,
                link.getRelationType(),
                link.getCurrent(),
                link.getCreatedAt());
    }
}

package com.example.item.domain;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Legacy placeholder (was item_document). Document linking now uses
 * document_link in the document module.
 * Kept for reference only; not mapped as an entity.
 */
@Deprecated
public class ItemDocument {

    private Item item;

    private UUID documentId;

    private RelationType relationType;

    private Boolean isCurrent = true;

    private OffsetDateTime createdAt;

    public enum RelationType {
        PRIMARY, REFERENCE
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public RelationType getRelationType() {
        return relationType;
    }

    public void setRelationType(RelationType relationType) {
        this.relationType = relationType;
    }

    public Boolean getCurrent() {
        return isCurrent;
    }

    public void setCurrent(Boolean current) {
        isCurrent = current;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public static class ItemDocumentId implements Serializable {
        private UUID item;
        private UUID documentId;

        public ItemDocumentId() {
        }

        public ItemDocumentId(UUID item, UUID document) {
            this.item = item;
            this.documentId = document;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            ItemDocumentId that = (ItemDocumentId) o;
            return Objects.equals(item, that.item) && Objects.equals(documentId, that.documentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(item, documentId);
        }
    }
}

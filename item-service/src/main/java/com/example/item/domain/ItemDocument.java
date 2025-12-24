package com.example.item.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "item_document", indexes = {
        @Index(name = "idx_item_document_item", columnList = "item_id"),
        @Index(name = "idx_item_document_doc", columnList = "document_id")
})
@IdClass(ItemDocument.ItemDocumentId.class)
public class ItemDocument {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 16)
    private RelationType relationType;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public enum RelationType {
        PRIMARY, REFERENCE
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
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
        private UUID document;

        public ItemDocumentId() {
        }

        public ItemDocumentId(UUID item, UUID document) {
            this.item = item;
            this.document = document;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemDocumentId that = (ItemDocumentId) o;
            return Objects.equals(item, that.item) && Objects.equals(document, that.document);
        }

        @Override
        public int hashCode() {
            return Objects.hash(item, document);
        }
    }
}




package com.example.myerp.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bom", indexes = {
    @Index(name = "idx_bom_product", columnList = "product_item_id"),
    @Index(name = "idx_bom_status", columnList = "status")
})
public class Bom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_item_id", nullable = false)
    private Item productItem;

    @Column(name = "revision", nullable = false, length = 10)
    private String revision = "A";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BomStatus status;

    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "bom", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<BomLine> bomLines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
        if (effectiveFrom == null) {
            effectiveFrom = OffsetDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Constructors
    public Bom() {
    }

    public Bom(Item productItem, String revision, BomStatus status) {
        this.productItem = productItem;
        this.revision = revision;
        this.status = status;
        this.effectiveFrom = OffsetDateTime.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Item getProductItem() {
        return productItem;
    }

    public void setProductItem(Item productItem) {
        this.productItem = productItem;
    }

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }

    public BomStatus getStatus() {
        return status;
    }

    public void setStatus(BomStatus status) {
        this.status = status;
    }

    public OffsetDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(OffsetDateTime effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public OffsetDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(OffsetDateTime effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<BomLine> getBomLines() {
        return bomLines;
    }

    public void setBomLines(List<BomLine> bomLines) {
        this.bomLines = bomLines;
    }

    public void addBomLine(BomLine bomLine) {
        bomLines.add(bomLine);
        bomLine.setBom(this);
    }

    public void removeBomLine(BomLine bomLine) {
        bomLines.remove(bomLine);
        bomLine.setBom(null);
    }

    // Enum for BomStatus
    public enum BomStatus {
        DRAFT, ACTIVE, OBSOLETE
    }
}


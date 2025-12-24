package com.example.item.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bom_line", uniqueConstraints = {
        @UniqueConstraint(name = "ux_bom_line_line_no", columnNames = { "bom_id", "line_no" }),
        @UniqueConstraint(name = "ux_bom_line_component", columnNames = { "bom_id", "component_item_id" })
}, indexes = {
        @Index(name = "idx_bom_line_bom", columnList = "bom_id"),
        @Index(name = "idx_bom_line_component", columnList = "component_item_id")
})
public class BomLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bom_id", nullable = false)
    private Bom bom;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_item_id", nullable = false)
    private Item componentItem;

    @Column(name = "qty_per", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyPer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uom_id", nullable = false)
    private Uom uom;

    @Column(name = "scrap_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal scrapRate = BigDecimal.ZERO;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (scrapRate == null) {
            scrapRate = BigDecimal.ZERO;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Bom getBom() {
        return bom;
    }

    public void setBom(Bom bom) {
        this.bom = bom;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    public Item getComponentItem() {
        return componentItem;
    }

    public void setComponentItem(Item componentItem) {
        this.componentItem = componentItem;
    }

    public BigDecimal getQtyPer() {
        return qtyPer;
    }

    public void setQtyPer(BigDecimal qtyPer) {
        this.qtyPer = qtyPer;
    }

    public Uom getUom() {
        return uom;
    }

    public void setUom(Uom uom) {
        this.uom = uom;
    }

    public BigDecimal getScrapRate() {
        return scrapRate;
    }

    public void setScrapRate(BigDecimal scrapRate) {
        this.scrapRate = scrapRate;
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
}


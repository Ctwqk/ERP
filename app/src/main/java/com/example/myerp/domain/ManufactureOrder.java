package com.example.myerp.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "manufacture_order", uniqueConstraints = {
    @UniqueConstraint(name = "ux_manufacture_order_code", columnNames = "manufacture_order_code")
}, indexes = {
    @Index(name = "idx_mo_order_line", columnList = "order_line_id"),
    @Index(name = "idx_mo_product_item", columnList = "product_item_id"),
    @Index(name = "idx_mo_status", columnList = "status"),
    @Index(name = "idx_mo_planned_start", columnList = "planned_start"),
    @Index(name = "idx_mo_planned_end", columnList = "planned_end"),
    @Index(name = "idx_mo_actual_start", columnList = "actual_start"),
    @Index(name = "idx_mo_actual_end", columnList = "actual_end")
})
public class ManufactureOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "manufacture_order_code", nullable = false, unique = true)
    private String manufactureOrderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_line_id")
    private OrderLine orderLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_item_id", nullable = false)
    private Item productItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bom_id")
    private Bom bom;

    @Column(name = "qty_planned", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyPlanned;

    @Column(name = "qty_completed", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyCompleted = BigDecimal.ZERO;

    @Column(name = "qty_scrapped", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyScrapped = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MoStatus status;

    @Column(name = "planned_start")
    private OffsetDateTime plannedStart;

    @Column(name = "planned_end")
    private OffsetDateTime plannedEnd;

    @Column(name = "actual_start")
    private OffsetDateTime actualStart;

    @Column(name = "actual_end")
    private OffsetDateTime actualEnd;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "manufactureOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
        if (qtyCompleted == null) {
            qtyCompleted = BigDecimal.ZERO;
        }
        if (qtyScrapped == null) {
            qtyScrapped = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Constructors
    public ManufactureOrder() {
    }

    public ManufactureOrder(String manufactureOrderCode, Item productItem, BigDecimal qtyPlanned, MoStatus status) {
        this.manufactureOrderCode = manufactureOrderCode;
        this.productItem = productItem;
        this.qtyPlanned = qtyPlanned;
        this.status = status;
        this.qtyCompleted = BigDecimal.ZERO;
        this.qtyScrapped = BigDecimal.ZERO;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getManufactureOrderCode() {
        return manufactureOrderCode;
    }

    public void setManufactureOrderCode(String manufactureOrderCode) {
        this.manufactureOrderCode = manufactureOrderCode;
    }

    public OrderLine getOrderLine() {
        return orderLine;
    }

    public void setOrderLine(OrderLine orderLine) {
        this.orderLine = orderLine;
    }

    public Item getProductItem() {
        return productItem;
    }

    public void setProductItem(Item productItem) {
        this.productItem = productItem;
    }

    public Bom getBom() {
        return bom;
    }

    public void setBom(Bom bom) {
        this.bom = bom;
    }

    public BigDecimal getQtyPlanned() {
        return qtyPlanned;
    }

    public void setQtyPlanned(BigDecimal qtyPlanned) {
        this.qtyPlanned = qtyPlanned;
    }

    public BigDecimal getQtyCompleted() {
        return qtyCompleted;
    }

    public void setQtyCompleted(BigDecimal qtyCompleted) {
        this.qtyCompleted = qtyCompleted;
    }

    public BigDecimal getQtyScrapped() {
        return qtyScrapped;
    }

    public void setQtyScrapped(BigDecimal qtyScrapped) {
        this.qtyScrapped = qtyScrapped;
    }

    public MoStatus getStatus() {
        return status;
    }

    public void setStatus(MoStatus status) {
        this.status = status;
    }

    public OffsetDateTime getPlannedStart() {
        return plannedStart;
    }

    public void setPlannedStart(OffsetDateTime plannedStart) {
        this.plannedStart = plannedStart;
    }

    public OffsetDateTime getPlannedEnd() {
        return plannedEnd;
    }

    public void setPlannedEnd(OffsetDateTime plannedEnd) {
        this.plannedEnd = plannedEnd;
    }

    public OffsetDateTime getActualStart() {
        return actualStart;
    }

    public void setActualStart(OffsetDateTime actualStart) {
        this.actualStart = actualStart;
    }

    public OffsetDateTime getActualEnd() {
        return actualEnd;
    }

    public void setActualEnd(OffsetDateTime actualEnd) {
        this.actualEnd = actualEnd;
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

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    public void addTask(Task task) {
        tasks.add(task);
        task.setManufactureOrder(this);
    }

    public void removeTask(Task task) {
        tasks.remove(task);
        task.setManufactureOrder(null);
    }

    // Enum for MoStatus
    public enum MoStatus {
        DRAFT, RELEASED, IN_PROGRESS, HOLD, CLOSED, CANCELLED
    }
}


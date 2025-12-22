package com.example.myerp.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "task", uniqueConstraints = {
    @UniqueConstraint(name = "ux_task_code", columnNames = "task_code")
}, indexes = {
    @Index(name = "idx_task_assignee", columnList = "assignee_user_id"),
    @Index(name = "idx_task_status", columnList = "task_status"),
    @Index(name = "idx_task_date", columnList = "task_date"),
    @Index(name = "idx_task_start_time", columnList = "start_time"),
    @Index(name = "idx_task_end_time", columnList = "end_time"),
    @Index(name = "idx_task_mo", columnList = "manufacture_order_id")
})
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacture_order_id", nullable = false)
    private ManufactureOrder manufactureOrder;

    @Column(name = "task_code", nullable = false, unique = true)
    private String taskCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 20)
    private TaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_status", nullable = false, length = 20)
    private TaskStatus taskStatus;

    @Column(name = "task_note", columnDefinition = "TEXT")
    private String taskNote;

    @Column(name = "task_date", nullable = false)
    private OffsetDateTime taskDate;

    @Column(name = "qty_planned", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyPlanned;

    @Column(name = "qty_produced", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyProduced = BigDecimal.ZERO;

    @Column(name = "qty_scrap", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyScrap = BigDecimal.ZERO;

    @Column(name = "qty_scrap_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal qtyScrapRate = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_user_id", nullable = false)
    private AppUser assigneeUser;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
        if (qtyProduced == null) {
            qtyProduced = BigDecimal.ZERO;
        }
        if (qtyScrap == null) {
            qtyScrap = BigDecimal.ZERO;
        }
        if (qtyScrapRate == null) {
            qtyScrapRate = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Constructors
    public Task() {
    }

    public Task(ManufactureOrder manufactureOrder, String taskCode, TaskType taskType, 
                TaskStatus taskStatus, OffsetDateTime taskDate, BigDecimal qtyPlanned, 
                AppUser assigneeUser, OffsetDateTime startTime) {
        this.manufactureOrder = manufactureOrder;
        this.taskCode = taskCode;
        this.taskType = taskType;
        this.taskStatus = taskStatus;
        this.taskDate = taskDate;
        this.qtyPlanned = qtyPlanned;
        this.assigneeUser = assigneeUser;
        this.startTime = startTime;
        this.qtyProduced = BigDecimal.ZERO;
        this.qtyScrap = BigDecimal.ZERO;
        this.qtyScrapRate = BigDecimal.ZERO;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ManufactureOrder getManufactureOrder() {
        return manufactureOrder;
    }

    public void setManufactureOrder(ManufactureOrder manufactureOrder) {
        this.manufactureOrder = manufactureOrder;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(TaskType taskType) {
        this.taskType = taskType;
    }

    public TaskStatus getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(TaskStatus taskStatus) {
        this.taskStatus = taskStatus;
    }

    public String getTaskNote() {
        return taskNote;
    }

    public void setTaskNote(String taskNote) {
        this.taskNote = taskNote;
    }

    public OffsetDateTime getTaskDate() {
        return taskDate;
    }

    public void setTaskDate(OffsetDateTime taskDate) {
        this.taskDate = taskDate;
    }

    public BigDecimal getQtyPlanned() {
        return qtyPlanned;
    }

    public void setQtyPlanned(BigDecimal qtyPlanned) {
        this.qtyPlanned = qtyPlanned;
    }

    public BigDecimal getQtyProduced() {
        return qtyProduced;
    }

    public void setQtyProduced(BigDecimal qtyProduced) {
        this.qtyProduced = qtyProduced;
    }

    public BigDecimal getQtyScrap() {
        return qtyScrap;
    }

    public void setQtyScrap(BigDecimal qtyScrap) {
        this.qtyScrap = qtyScrap;
    }

    public BigDecimal getQtyScrapRate() {
        return qtyScrapRate;
    }

    public void setQtyScrapRate(BigDecimal qtyScrapRate) {
        this.qtyScrapRate = qtyScrapRate;
    }

    public AppUser getAssigneeUser() {
        return assigneeUser;
    }

    public void setAssigneeUser(AppUser assigneeUser) {
        this.assigneeUser = assigneeUser;
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(OffsetDateTime startTime) {
        this.startTime = startTime;
    }

    public OffsetDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(OffsetDateTime endTime) {
        this.endTime = endTime;
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

    // Enums
    public enum TaskType {
        PRODUCE, REPAIR, MAINTENANCE
    }

    public enum TaskStatus {
        PENDING, CONFIRMED, IN_PROGRESS, DONE, CANCELLED
    }
}


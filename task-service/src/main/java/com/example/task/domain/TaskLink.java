package com.example.task.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "task_link", indexes = {
        @Index(name = "idx_task_link_task", columnList = "task_id"),
        @Index(name = "idx_task_link_reftype", columnList = "ref_type")
})
public class TaskLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", nullable = false, length = 16)
    private RefType refType;

    @Column(name = "ref_id", nullable = false, length = 128)
    private String refId;

    @Column(name = "ref_meta", columnDefinition = "jsonb")
    private String refMeta;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public enum RefType {
        USER, INVENTORY, ORDER, ITEM, DOCUMENT, MO
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public RefType getRefType() { return refType; }
    public void setRefType(RefType refType) { this.refType = refType; }
    public String getRefId() { return refId; }
    public void setRefId(String refId) { this.refId = refId; }
    public String getRefMeta() { return refMeta; }
    public void setRefMeta(String refMeta) { this.refMeta = refMeta; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}


package com.example.item.dto;

import com.example.item.domain.Item;
import com.example.item.domain.Item.ItemType;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

public class ItemDto {
    private UUID id;
    private String skuCode;
    private String name;
    private ItemType itemType;
    private UUID baseUomId;
    private Boolean active;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<UUID> documentIds;

    public ItemDto() {
    }

    public ItemDto(Item item) {
        this.id = item.getId();
        this.skuCode = item.getSkuCode();
        this.name = item.getName();
        this.itemType = item.getItemType();
        this.baseUomId = item.getBaseUom() != null ? item.getBaseUom().getId() : null;
        this.active = item.getActive();
        this.description = item.getDescription();
        this.createdAt = item.getCreatedAt();
        this.updatedAt = item.getUpdatedAt();
    }

    public List<UUID> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<UUID> documentIds) {
        this.documentIds = documentIds;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    public UUID getBaseUomId() {
        return baseUomId;
    }

    public void setBaseUomId(UUID baseUomId) {
        this.baseUomId = baseUomId;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
}

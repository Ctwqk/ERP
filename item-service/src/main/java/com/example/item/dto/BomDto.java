package com.example.item.dto;

import com.example.item.domain.Bom;
import com.example.item.domain.Bom.BomStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BomDto {
        private UUID id;
        private UUID productItemId;
        private String revision;
        private BomStatus status;
        private OffsetDateTime effectiveFrom;
        private OffsetDateTime effectiveTo;
        private String note;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
        private List<BomLineDto> lines;

        public BomDto() {
        }

        public BomDto(Bom bom) {
                this.id = bom.getId();
                this.productItemId = bom.getProductItem() != null ? bom.getProductItem().getId() : null;
                this.revision = bom.getRevision();
                this.status = bom.getStatus();
                this.effectiveFrom = bom.getEffectiveFrom();
                this.effectiveTo = bom.getEffectiveTo();
                this.note = bom.getNote();
                this.createdAt = bom.getCreatedAt();
                this.updatedAt = bom.getUpdatedAt();
                this.lines = bom.getLines() == null ? null
                                : bom.getLines().stream().map(BomLineDto::new).collect(Collectors.toList());
        }

        public UUID getId() {
                return id;
        }

        public void setId(UUID id) {
                this.id = id;
        }

        public UUID getProductItemId() {
                return productItemId;
        }

        public void setProductItemId(UUID productItemId) {
                this.productItemId = productItemId;
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

        public List<BomLineDto> getLines() {
                return lines;
        }

        public void setLines(List<BomLineDto> lines) {
                this.lines = lines;
        }
}
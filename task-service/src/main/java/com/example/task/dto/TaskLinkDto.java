package com.example.task.dto;

import com.example.task.domain.TaskLink;
import com.example.task.domain.TaskLink.RefType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskLinkDto(
        UUID id,
        UUID taskId,
        RefType refType,
        String refId,
        String refMeta,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public TaskLinkDto(TaskLink link) {
        this(link.getId(),
                link.getTaskId(),
                link.getRefType(),
                link.getRefId(),
                link.getRefMeta(),
                link.getCreatedAt(),
                link.getUpdatedAt());
    }
}


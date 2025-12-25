package com.example.task.dto;

import com.example.task.domain.Task;
import com.example.task.domain.Task.Priority;
import com.example.task.domain.Task.Status;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TaskDto(
        UUID id,
        String title,
        String description,
        Status status,
        Priority priority,
        OffsetDateTime dueAt,
        UUID assigneeUserId,
        UUID createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt,
        List<TaskLinkDto> links
) {
    public TaskDto(Task task, List<TaskLinkDto> links) {
        this(task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueAt(),
                task.getAssigneeUserId(),
                task.getCreatedByUserId(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt(),
                links);
    }
}


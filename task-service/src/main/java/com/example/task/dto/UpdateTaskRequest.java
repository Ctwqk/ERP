package com.example.task.dto;

import com.example.task.domain.Task.Priority;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateTaskRequest(
        String title,
        String description,
        Priority priority,
        OffsetDateTime dueAt,
        UUID assigneeUserId
) {
}


package com.example.task.dto;

import com.example.task.domain.Task.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank String title,
        String description,
        @NotNull Priority priority,
        OffsetDateTime dueAt,
        UUID assigneeUserId
) {
}


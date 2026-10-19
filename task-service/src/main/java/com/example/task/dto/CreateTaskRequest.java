package com.example.task.dto;

import com.example.task.domain.Task.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank String title,
        String description,
        Priority priority,
        OffsetDateTime dueAt,
        UUID assigneeUserId,
        UUID prerequisiteItemId,
        @DecimalMin(value = "0", inclusive = false) BigDecimal prerequisiteItemQuantity
) {
}


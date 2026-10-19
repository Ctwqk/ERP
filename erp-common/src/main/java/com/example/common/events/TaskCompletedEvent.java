package com.example.common.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Published by task-service when a task reaches {@code DONE}. */
public record TaskCompletedEvent(
        UUID taskId,
        String title,
        UUID createdByUserId,
        UUID assigneeUserId,
        OffsetDateTime completedAt) {
}

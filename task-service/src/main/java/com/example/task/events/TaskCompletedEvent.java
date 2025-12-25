package com.example.task.events;

import java.util.UUID;

public record TaskCompletedEvent(UUID taskId) {
}


package com.example.task.events;

import java.util.UUID;

public record InventoryInEvent(UUID itemId, double quantity) {
}


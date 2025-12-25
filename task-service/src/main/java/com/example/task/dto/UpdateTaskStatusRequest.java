package com.example.task.dto;

import com.example.task.domain.Task.Status;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(@NotNull Status status) {
}


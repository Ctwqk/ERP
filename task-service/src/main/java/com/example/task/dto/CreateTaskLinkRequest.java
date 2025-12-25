package com.example.task.dto;

import com.example.task.domain.TaskLink.RefType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTaskLinkRequest(
        @NotNull RefType refType,
        @NotBlank String refId,
        String refMeta
) {
}


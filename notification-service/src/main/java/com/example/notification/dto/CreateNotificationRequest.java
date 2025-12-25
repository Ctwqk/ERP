package com.example.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateNotificationRequest(
        @NotBlank String title,
        String content,
        @NotBlank String type,
        @NotNull UUID recipientUserId
) {
}


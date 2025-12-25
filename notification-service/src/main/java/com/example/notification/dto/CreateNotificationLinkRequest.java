package com.example.notification.dto;

import com.example.notification.domain.NotificationLink.LinkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateNotificationLinkRequest(
        @NotNull LinkType linkType,
        @NotBlank String refId,
        String type
) {
}


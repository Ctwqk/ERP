package com.example.notification.dto;

import com.example.notification.domain.NotificationLink;
import com.example.notification.domain.NotificationLink.LinkType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationLinkDto(
        UUID id,
        UUID notificationId,
        LinkType linkType,
        String refId,
        String type,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public NotificationLinkDto(NotificationLink link) {
        this(link.getId(),
                link.getNotificationId(),
                link.getLinkType(),
                link.getRefId(),
                link.getType(),
                link.getCreatedAt(),
                link.getUpdatedAt());
    }
}


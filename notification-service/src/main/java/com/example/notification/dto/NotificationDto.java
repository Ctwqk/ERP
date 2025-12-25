package com.example.notification.dto;

import com.example.notification.domain.Notification;
import com.example.notification.domain.Notification.Status;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        String title,
        String content,
        String type,
        Status status,
        UUID recipientUserId,
        UUID createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime readAt,
        List<NotificationLinkDto> links
) {
    public NotificationDto(Notification n, List<NotificationLinkDto> links) {
        this(n.getId(),
                n.getTitle(),
                n.getContent(),
                n.getType(),
                n.getStatus(),
                n.getRecipientUserId(),
                n.getCreatedByUserId(),
                n.getCreatedAt(),
                n.getUpdatedAt(),
                n.getReadAt(),
                links);
    }
}


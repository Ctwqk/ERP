package com.example.notification.service;

import com.example.notification.dto.CreateNotificationLinkRequest;
import com.example.notification.dto.CreateNotificationRequest;
import com.example.notification.dto.MarkReadRequest;
import com.example.notification.dto.NotificationDto;
import com.example.notification.dto.NotificationLinkDto;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    NotificationDto create(CreateNotificationRequest request);

    NotificationDto get(UUID id);

    Page<NotificationDto> listMine(Pageable pageable, String status);

    NotificationDto markRead(UUID id, MarkReadRequest request);

    NotificationLinkDto addLink(UUID notificationId, CreateNotificationLinkRequest request);

    void deleteLink(UUID notificationId, UUID linkId);
}


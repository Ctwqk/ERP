package com.example.notification.repository;

import com.example.notification.domain.NotificationLink;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLinkRepository extends JpaRepository<NotificationLink, UUID> {
    List<NotificationLink> findByNotificationId(UUID notificationId);
}


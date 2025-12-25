package com.example.notification.service;

import com.example.notification.domain.Notification;
import com.example.notification.domain.Notification.Status;
import com.example.notification.domain.NotificationLink;
import com.example.notification.dto.CreateNotificationLinkRequest;
import com.example.notification.dto.CreateNotificationRequest;
import com.example.notification.dto.MarkReadRequest;
import com.example.notification.dto.NotificationDto;
import com.example.notification.dto.NotificationLinkDto;
import com.example.notification.repository.NotificationLinkRepository;
import com.example.notification.repository.NotificationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationLinkRepository notificationLinkRepository;
    private final CurrentUserProvider currentUserProvider;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
            NotificationLinkRepository notificationLinkRepository,
            CurrentUserProvider currentUserProvider) {
        this.notificationRepository = notificationRepository;
        this.notificationLinkRepository = notificationLinkRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public NotificationDto create(CreateNotificationRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is required");
        }
        Notification n = new Notification();
        n.setTitle(request.title());
        n.setContent(request.content());
        n.setType(request.type());
        n.setRecipientUserId(request.recipientUserId());
        n.setCreatedByUserId(currentUserUuid());
        n.setStatus(Status.UNREAD);
        Notification saved = notificationRepository.save(n);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationDto get(UUID id) {
        Notification n = find(id);
        assertOwner(n);
        return toDto(n);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDto> listMine(Pageable pageable, String status) {
        UUID me = currentUserUuid();
        Page<Notification> page;
        if (status != null && !status.isBlank()) {
            Status st = parseStatus(status);
            page = notificationRepository.findByRecipientUserIdAndStatus(me, st, pageable);
        } else {
            page = notificationRepository.findByRecipientUserId(me, pageable);
        }
        return page.map(this::toDto);
    }

    @Override
    public NotificationDto markRead(UUID id, MarkReadRequest request) {
        Notification n = find(id);
        assertOwner(n);
        boolean markRead = request != null && request.read();
        if (markRead && n.getStatus() != Status.READ) {
            n.setStatus(Status.READ);
            n.setReadAt(OffsetDateTime.now());
        } else if (!markRead && n.getStatus() != Status.UNREAD) {
            n.setStatus(Status.UNREAD);
            n.setReadAt(null);
        }
        Notification saved = notificationRepository.save(n);
        return toDto(saved);
    }

    @Override
    public NotificationLinkDto addLink(UUID notificationId, CreateNotificationLinkRequest request) {
        Notification n = find(notificationId);
        assertOwner(n);
        NotificationLink link = new NotificationLink();
        link.setNotificationId(n.getId());
        link.setLinkType(request.linkType());
        link.setRefId(request.refId());
        link.setType(request.type());
        NotificationLink saved = notificationLinkRepository.save(link);
        return new NotificationLinkDto(saved);
    }

    @Override
    public void deleteLink(UUID notificationId, UUID linkId) {
        Notification n = find(notificationId);
        assertOwner(n);
        NotificationLink link = notificationLinkRepository.findById(linkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found"));
        if (!link.getNotificationId().equals(notificationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link not belong to notification");
        }
        notificationLinkRepository.delete(link);
    }

    private Notification find(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    private void assertOwner(Notification n) {
        UUID me = currentUserUuid();
        if (!me.equals(n.getRecipientUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your notification");
        }
    }

    private UUID currentUserUuid() {
        return UUID.fromString(currentUserProvider.getCurrentUserId());
    }

    private Status parseStatus(String status) {
        try {
            return Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
        }
    }

    private NotificationDto toDto(Notification n) {
        List<NotificationLinkDto> links = notificationLinkRepository.findByNotificationId(n.getId()).stream()
                .map(NotificationLinkDto::new)
                .collect(Collectors.toList());
        return new NotificationDto(n, links);
    }
}


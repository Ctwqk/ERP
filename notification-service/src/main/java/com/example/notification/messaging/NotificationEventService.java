package com.example.notification.messaging;

import com.example.common.events.DocumentUploadEvent;
import com.example.common.events.TaskCompletedEvent;
import com.example.notification.domain.Notification;
import com.example.notification.domain.Notification.Status;
import com.example.notification.domain.NotificationLink;
import com.example.notification.domain.NotificationLink.LinkType;
import com.example.notification.repository.NotificationLinkRepository;
import com.example.notification.repository.NotificationRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns domain events from other services into notifications. Processing is idempotent: every notification derived
 * from an event carries a deterministic dedupe key, so a redelivered message is acknowledged without side effects.
 */
@Service
@Transactional
public class NotificationEventService {

    /** Author recorded for notifications that were not created by a person. */
    public static final UUID SYSTEM_USER = new UUID(0L, 0L);

    public static final String TYPE_TASK_COMPLETED = "TASK_COMPLETED";
    public static final String TYPE_DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";

    private static final Logger log = LoggerFactory.getLogger(NotificationEventService.class);

    private final NotificationRepository notifications;
    private final NotificationLinkRepository links;

    public NotificationEventService(NotificationRepository notifications, NotificationLinkRepository links) {
        this.notifications = notifications;
        this.links = links;
    }

    /** Notifies the task's creator and assignee (each once) that the task is done. */
    public int onTaskCompleted(TaskCompletedEvent event) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (event.createdByUserId() != null) {
            recipients.add(event.createdByUserId());
        }
        if (event.assigneeUserId() != null) {
            recipients.add(event.assigneeUserId());
        }
        int created = 0;
        for (UUID recipient : recipients) {
            String key = "task-completed:%s:%s".formatted(event.taskId(), recipient);
            if (create(key, recipient, TYPE_TASK_COMPLETED, "Task completed: " + event.title(),
                    "Task \"%s\" has been marked as done.".formatted(event.title()),
                    LinkType.TASK, String.valueOf(event.taskId()))) {
                created++;
            }
        }
        return created;
    }

    /** Tells the uploader that the document is stored and verified. */
    public int onDocumentUploaded(DocumentUploadEvent event) {
        if (event.uploadedByUserId() == null || event.documentIds() == null) {
            log.debug("Ignoring document event without uploader or documents");
            return 0;
        }
        int created = 0;
        for (UUID documentId : event.documentIds()) {
            String key = "document-uploaded:%s:%s".formatted(documentId, event.uploadedByUserId());
            String kind = event.documentType() != null ? event.documentType() : "document";
            if (create(key, event.uploadedByUserId(), TYPE_DOCUMENT_UPLOADED, "Document uploaded",
                    "Your %s upload %s is available.".formatted(kind, documentId),
                    LinkType.DOCUMENT, documentId.toString())) {
                created++;
            }
        }
        return created;
    }

    private boolean create(String dedupeKey, UUID recipient, String type, String title, String content,
            LinkType linkType, String refId) {
        if (notifications.existsByDedupeKey(dedupeKey)) {
            log.debug("Duplicate event ignored: {}", dedupeKey);
            return false;
        }
        Notification n = new Notification();
        n.setTitle(title);
        n.setContent(content);
        n.setType(type);
        n.setStatus(Status.UNREAD);
        n.setRecipientUserId(recipient);
        n.setCreatedByUserId(SYSTEM_USER);
        n.setDedupeKey(dedupeKey);
        Notification saved = notifications.save(n);

        NotificationLink link = new NotificationLink();
        link.setNotificationId(saved.getId());
        link.setLinkType(linkType);
        link.setRefId(refId);
        link.setType(type);
        links.save(link);
        return true;
    }
}

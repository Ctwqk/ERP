package com.example.notification.messaging;

import com.example.common.events.DocumentUploadEvent;
import com.example.common.events.TaskCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationEventService events;

    public NotificationEventListener(NotificationEventService events) {
        this.events = events;
    }

    @RabbitListener(queues = NotificationMessagingConfig.TASK_COMPLETED_QUEUE)
    public void onTaskCompleted(TaskCompletedEvent event) {
        int created = events.onTaskCompleted(event);
        log.info("task.completed {} -> {} notification(s)", event.taskId(), created);
    }

    @RabbitListener(queues = NotificationMessagingConfig.DOCUMENT_UPLOADED_QUEUE)
    public void onDocumentUploaded(DocumentUploadEvent event) {
        int created = events.onDocumentUploaded(event);
        log.info("document upload {} -> {} notification(s)", event.documentIds(), created);
    }
}

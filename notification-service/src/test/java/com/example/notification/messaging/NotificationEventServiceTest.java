package com.example.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.events.DocumentUploadEvent;
import com.example.common.events.TaskCompletedEvent;
import com.example.notification.domain.Notification;
import com.example.notification.repository.NotificationLinkRepository;
import com.example.notification.repository.NotificationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventServiceTest {

    @Mock NotificationRepository notifications;
    @Mock NotificationLinkRepository links;

    NotificationEventService service;

    @BeforeEach
    void setUp() {
        service = new NotificationEventService(notifications, links);
    }

    private void saveReturnsArgument() {
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });
    }

    @Test
    void taskCompletionNotifiesCreatorAndAssigneeOnce() {
        saveReturnsArgument();
        UUID creator = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        TaskCompletedEvent event = new TaskCompletedEvent(UUID.randomUUID(), "Pack order", creator, assignee,
                OffsetDateTime.now());

        assertThat(service.onTaskCompleted(event)).isEqualTo(2);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getRecipientUserId)
                .containsExactly(creator, assignee);
        assertThat(saved.getAllValues()).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationEventService.TYPE_TASK_COMPLETED);
            assertThat(n.getTitle()).contains("Pack order");
            assertThat(n.getCreatedByUserId()).isEqualTo(NotificationEventService.SYSTEM_USER);
            assertThat(n.getDedupeKey()).contains(event.taskId().toString());
        });
        verify(links, times(2)).save(any());
    }

    @Test
    void creatorWhoIsAlsoAssigneeGetsASingleNotification() {
        saveReturnsArgument();
        UUID user = UUID.randomUUID();

        assertThat(service.onTaskCompleted(
                new TaskCompletedEvent(UUID.randomUUID(), "t", user, user, OffsetDateTime.now()))).isEqualTo(1);
    }

    @Test
    void redeliveredEventIsIgnored() {
        when(notifications.existsByDedupeKey(anyString())).thenReturn(true);

        int created = service.onTaskCompleted(new TaskCompletedEvent(UUID.randomUUID(), "t", UUID.randomUUID(),
                null, OffsetDateTime.now()));

        assertThat(created).isZero();
        verify(notifications, never()).save(any());
    }

    @Test
    void documentUploadNotifiesTheUploader() {
        saveReturnsArgument();
        UUID uploader = UUID.randomUUID();
        UUID doc = UUID.randomUUID();

        int created = service.onDocumentUploaded(
                new DocumentUploadEvent(List.of(doc), "ORDER", UUID.randomUUID(), null, uploader, "a@b.c"));

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getRecipientUserId()).isEqualTo(uploader);
        assertThat(saved.getValue().getContent()).contains(doc.toString());
    }

    @Test
    void documentEventWithoutUploaderIsSkipped() {
        assertThat(service.onDocumentUploaded(
                new DocumentUploadEvent(List.of(UUID.randomUUID()), "ITEM", null, null, null, null))).isZero();
        verify(notifications, never()).save(any());
    }
}

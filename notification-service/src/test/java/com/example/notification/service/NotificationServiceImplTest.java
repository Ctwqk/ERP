package com.example.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.notification.domain.Notification;
import com.example.notification.domain.Notification.Status;
import com.example.notification.domain.NotificationLink;
import com.example.notification.domain.NotificationLink.LinkType;
import com.example.notification.dto.CreateNotificationLinkRequest;
import com.example.notification.dto.CreateNotificationRequest;
import com.example.notification.dto.MarkReadRequest;
import com.example.notification.dto.NotificationDto;
import com.example.notification.repository.NotificationLinkRepository;
import com.example.notification.repository.NotificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID SOMEONE_ELSE = UUID.randomUUID();

    @Mock NotificationRepository notifications;
    @Mock NotificationLinkRepository links;
    @Mock CurrentUserProvider currentUser;

    NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notifications, links, currentUser);
        lenient().when(currentUser.getCurrentUserId()).thenReturn(ME);
    }

    private Notification notificationFor(UUID recipient, Status status) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setTitle("t");
        n.setType("INFO");
        n.setStatus(status);
        n.setRecipientUserId(recipient);
        n.setCreatedByUserId(SOMEONE_ELSE);
        return n;
    }

    @Test
    void createStoresUnreadNotificationAuthoredByCaller() {
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationDto dto = service.create(new CreateNotificationRequest("Hi", "body", "INFO", SOMEONE_ELSE));

        assertThat(dto.status()).isEqualTo(Status.UNREAD);
        assertThat(dto.recipientUserId()).isEqualTo(SOMEONE_ELSE);
        assertThat(dto.createdByUserId()).isEqualTo(ME);
    }

    @Test
    void readingSomeoneElsesNotificationIsForbidden() {
        Notification n = notificationFor(SOMEONE_ELSE, Status.UNREAD);
        when(notifications.findById(n.getId())).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.get(n.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void unknownNotificationIsNotFound() {
        UUID id = UUID.randomUUID();
        when(notifications.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void markReadSetsTimestampAndCanBeUndone() {
        Notification n = notificationFor(ME, Status.UNREAD);
        when(notifications.findById(n.getId())).thenReturn(Optional.of(n));
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationDto read = service.markRead(n.getId(), new MarkReadRequest(true));
        assertThat(read.status()).isEqualTo(Status.READ);
        assertThat(read.readAt()).isNotNull();

        NotificationDto unread = service.markRead(n.getId(), new MarkReadRequest(false));
        assertThat(unread.status()).isEqualTo(Status.UNREAD);
        assertThat(unread.readAt()).isNull();
    }

    @Test
    void listMineFiltersByStatusAndRejectsUnknownStatus() {
        assertThatThrownBy(() -> service.listMine(org.springframework.data.domain.Pageable.unpaged(), "BOGUS"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        when(notifications.findByRecipientUserIdAndStatus(any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        service.listMine(org.springframework.data.domain.Pageable.unpaged(), "unread");
        verify(notifications).findByRecipientUserIdAndStatus(ME, Status.UNREAD,
                org.springframework.data.domain.Pageable.unpaged());
    }

    @Test
    void linkCanOnlyBeDeletedThroughItsOwnNotification() {
        Notification mine = notificationFor(ME, Status.UNREAD);
        NotificationLink foreign = new NotificationLink();
        foreign.setId(UUID.randomUUID());
        foreign.setNotificationId(UUID.randomUUID());
        when(notifications.findById(mine.getId())).thenReturn(Optional.of(mine));
        when(links.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.deleteLink(mine.getId(), foreign.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(links, never()).delete(any());
    }

    @Test
    void addLinkRequiresOwnership() {
        Notification theirs = notificationFor(SOMEONE_ELSE, Status.UNREAD);
        when(notifications.findById(theirs.getId())).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.addLink(theirs.getId(),
                new CreateNotificationLinkRequest(LinkType.TASK, "x", null)))
                .isInstanceOf(ResponseStatusException.class);
        verify(links, never()).save(any());
    }
}

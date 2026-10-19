package com.example.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.events.TaskCompletedEvent;
import com.example.task.domain.Task;
import com.example.task.domain.Task.Priority;
import com.example.task.domain.Task.Status;
import com.example.task.domain.TaskLink;
import com.example.task.domain.TaskLink.RefType;
import com.example.task.dto.CreateTaskLinkRequest;
import com.example.task.dto.CreateTaskRequest;
import com.example.task.dto.TaskDto;
import com.example.task.dto.UpdateTaskRequest;
import com.example.task.dto.UpdateTaskStatusRequest;
import com.example.task.ports.CorePort;
import com.example.task.ports.InventoryPort;
import com.example.task.ports.UserPort;
import com.example.task.repository.TaskLinkRepository;
import com.example.task.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID ITEM = UUID.randomUUID();

    @Mock TaskRepository tasks;
    @Mock TaskLinkRepository links;
    @Mock UserPort users;
    @Mock InventoryPort inventory;
    @Mock CorePort core;
    @Mock CurrentUserProvider currentUser;
    @Mock ApplicationEventPublisher events;

    TaskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TaskServiceImpl(tasks, links, users, inventory, core, currentUser, events, new ObjectMapper());
        lenient().when(currentUser.getCurrentUserId()).thenReturn(ME);
        lenient().when(tasks.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });
        lenient().when(links.findByTaskId(any())).thenReturn(List.of());
    }

    private Task existing(Status status) {
        Task t = new Task();
        t.setId(UUID.randomUUID());
        t.setTitle("Assemble");
        t.setStatus(status);
        t.setPriority(Priority.MEDIUM);
        t.setCreatedByUserId(ME);
        when(tasks.findById(t.getId())).thenReturn(Optional.of(t));
        return t;
    }

    private static ResponseStatusException status(Throwable t, HttpStatus expected) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        ResponseStatusException e = (ResponseStatusException) t;
        assertThat(e.getStatusCode()).isEqualTo(expected);
        return e;
    }

    @Test
    void createDefaultsToOpenMediumPriorityOwnedByCaller() {
        TaskDto dto = service.createTask(new CreateTaskRequest("Inspect", null, null, null, null, null, null));

        assertThat(dto.status()).isEqualTo(Status.OPEN);
        assertThat(dto.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(dto.createdByUserId()).isEqualTo(ME);
    }

    @Test
    void createRejectsUnknownAssignee() {
        UUID ghost = UUID.randomUUID();
        when(users.existsById(ghost)).thenReturn(false);

        assertThatThrownBy(() -> service.createTask(
                new CreateTaskRequest("Inspect", null, Priority.HIGH, null, ghost, null, null)))
                .satisfies(t -> status(t, HttpStatus.BAD_REQUEST));
        verify(tasks, never()).save(any());
    }

    @Test
    void prerequisiteNeedsBothItemAndPositiveQuantity() {
        assertThatThrownBy(() -> service.createTask(
                new CreateTaskRequest("t", null, null, null, null, ITEM, null)))
                .satisfies(t -> status(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createTask(
                new CreateTaskRequest("t", null, null, null, null, null, BigDecimal.ONE)))
                .satisfies(t -> status(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void prerequisiteItemMustExist() {
        when(core.existsItem(ITEM)).thenReturn(false);

        assertThatThrownBy(() -> service.createTask(
                new CreateTaskRequest("t", null, null, null, null, ITEM, BigDecimal.TEN)))
                .satisfies(t -> status(t, HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @CsvSource({
            "OPEN,IN_PROGRESS,true", "OPEN,CANCELED,true", "OPEN,DONE,false", "OPEN,OPEN,false",
            "IN_PROGRESS,DONE,true", "IN_PROGRESS,CANCELED,true", "IN_PROGRESS,OPEN,false",
            "DONE,OPEN,false", "DONE,CANCELED,false", "CANCELED,OPEN,false", "CANCELED,IN_PROGRESS,false"})
    void statusTransitionTable(Status from, Status to, boolean allowed) {
        assertThat(TaskServiceImpl.isValidTransition(from, to)).isEqualTo(allowed);
    }

    @Test
    void invalidTransitionIsConflict() {
        Task t = existing(Status.OPEN);

        assertThatThrownBy(() -> service.updateStatus(t.getId(), new UpdateTaskStatusRequest(Status.DONE)))
                .satisfies(e -> status(e, HttpStatus.CONFLICT));
    }

    @Test
    void startingTaskChecksPrerequisiteStock() {
        Task t = existing(Status.OPEN);
        t.setPrerequisiteItemId(ITEM);
        t.setPrerequisiteItemQuantity(new BigDecimal("25"));
        when(inventory.checkStockAvailable(ITEM, new BigDecimal("25"))).thenReturn(false);

        assertThatThrownBy(() -> service.updateStatus(t.getId(), new UpdateTaskStatusRequest(Status.IN_PROGRESS)))
                .satisfies(e -> assertThat(status(e, HttpStatus.CONFLICT).getReason())
                        .contains("Prerequisite not met"));
        assertThat(t.getStatus()).isEqualTo(Status.OPEN);

        when(inventory.checkStockAvailable(ITEM, new BigDecimal("25"))).thenReturn(true);
        assertThat(service.updateStatus(t.getId(), new UpdateTaskStatusRequest(Status.IN_PROGRESS)).status())
                .isEqualTo(Status.IN_PROGRESS);
    }

    @Test
    void taskWithoutPrerequisiteStartsWithoutCallingInventory() {
        Task t = existing(Status.OPEN);

        service.updateStatus(t.getId(), new UpdateTaskStatusRequest(Status.IN_PROGRESS));

        org.mockito.Mockito.verifyNoInteractions(inventory);
    }

    @Test
    void completingTaskStampsTimeAndPublishesEvent() {
        Task t = existing(Status.IN_PROGRESS);
        UUID assignee = UUID.randomUUID();
        t.setAssigneeUserId(assignee);

        TaskDto dto = service.updateStatus(t.getId(), new UpdateTaskStatusRequest(Status.DONE));

        assertThat(dto.completedAt()).isNotNull();
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue()).isInstanceOfSatisfying(TaskCompletedEvent.class, e -> {
            assertThat(e.taskId()).isEqualTo(t.getId());
            assertThat(e.title()).isEqualTo("Assemble");
            assertThat(e.createdByUserId()).isEqualTo(ME);
            assertThat(e.assigneeUserId()).isEqualTo(assignee);
        });
    }

    @Test
    void cancelingDoesNotPublishCompletion() {
        Task t = existing(Status.OPEN);

        service.updateStatus(t.getId(), new UpdateTaskStatusRequest(Status.CANCELED));

        verify(events, never()).publishEvent(any());
    }

    @Test
    void closedTasksCannotBeEdited() {
        Task done = existing(Status.DONE);

        assertThatThrownBy(() -> service.updateTask(done.getId(),
                new UpdateTaskRequest("new", null, null, null, null, null, null)))
                .satisfies(e -> status(e, HttpStatus.CONFLICT));
    }

    @Test
    void updateChangesOnlyProvidedFields() {
        Task t = existing(Status.OPEN);
        t.setDescription("keep me");

        TaskDto dto = service.updateTask(t.getId(),
                new UpdateTaskRequest("Renamed", null, Priority.HIGH, null, null, null, null));

        assertThat(dto.title()).isEqualTo("Renamed");
        assertThat(dto.priority()).isEqualTo(Priority.HIGH);
        assertThat(dto.description()).isEqualTo("keep me");
    }

    @Test
    void unknownTaskIsNotFound() {
        UUID id = UUID.randomUUID();
        when(tasks.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTask(id)).satisfies(e -> status(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void listMineOnlySupportsMe() {
        assertThatThrownBy(() -> service.listMyTasks("someone-else", null, org.springframework.data.domain.Pageable.unpaged()))
                .satisfies(e -> status(e, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.listMyTasks("me", "NOPE", org.springframework.data.domain.Pageable.unpaged()))
                .satisfies(e -> status(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void linkReferencesAreVerifiedPerType() {
        Task t = existing(Status.OPEN);
        UUID doc = UUID.randomUUID();
        when(core.existsDocument(doc)).thenReturn(false);

        assertThatThrownBy(() -> service.addLink(t.getId(),
                new CreateTaskLinkRequest(RefType.DOCUMENT, doc.toString(), null)))
                .satisfies(e -> status(e, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.addLink(t.getId(),
                new CreateTaskLinkRequest(RefType.ITEM, "not-a-uuid", null)))
                .satisfies(e -> status(e, HttpStatus.BAD_REQUEST));
        verify(links, never()).save(any());
    }

    @Test
    void opaqueLinkTypesAreAcceptedAndMetaMustBeAJsonObject() {
        Task t = existing(Status.OPEN);
        when(links.save(any(TaskLink.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.addLink(t.getId(), new CreateTaskLinkRequest(RefType.MO, "MO-2024-001", "{\"a\": 1}"))
                .refMeta()).isEqualTo("{\"a\":1}");
        assertThatThrownBy(() -> service.addLink(t.getId(),
                new CreateTaskLinkRequest(RefType.MO, "MO-1", "{broken")))
                .satisfies(e -> status(e, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.addLink(t.getId(),
                new CreateTaskLinkRequest(RefType.MO, "MO-1", "[1,2]")))
                .satisfies(e -> status(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void deleteLinkMustMatchTask() {
        UUID taskId = UUID.randomUUID();
        TaskLink link = new TaskLink();
        link.setId(UUID.randomUUID());
        link.setTaskId(UUID.randomUUID());
        when(links.findById(link.getId())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.deleteLink(taskId, link.getId()))
                .satisfies(e -> status(e, HttpStatus.BAD_REQUEST));
        verify(links, never()).delete(any());
    }
}

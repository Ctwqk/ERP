package com.example.task.service;

import com.example.task.domain.Task;
import com.example.task.domain.Task.Priority;
import com.example.task.domain.Task.Status;
import com.example.task.domain.TaskLink;
import com.example.task.domain.TaskLink.RefType;
import com.example.task.dto.CreateTaskLinkRequest;
import com.example.task.dto.CreateTaskRequest;
import com.example.task.dto.TaskDto;
import com.example.task.dto.TaskLinkDto;
import com.example.task.dto.UpdateTaskRequest;
import com.example.task.dto.UpdateTaskStatusRequest;
import com.example.task.events.TaskCompletedEvent;
import com.example.task.events.TaskReadyForMoEvent;
import com.example.task.ports.CorePort;
import com.example.task.ports.InventoryPort;
import com.example.task.ports.UserPort;
import com.example.task.repository.TaskLinkRepository;
import com.example.task.repository.TaskRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskLinkRepository taskLinkRepository;
    private final UserPort userPort;
    private final InventoryPort inventoryPort;
    private final CorePort corePort;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher eventPublisher;

    public TaskServiceImpl(TaskRepository taskRepository,
            TaskLinkRepository taskLinkRepository,
            UserPort userPort,
            InventoryPort inventoryPort,
            CorePort corePort,
            CurrentUserProvider currentUserProvider,
            ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.taskLinkRepository = taskLinkRepository;
        this.userPort = userPort;
        this.inventoryPort = inventoryPort;
        this.corePort = corePort;
        this.currentUserProvider = currentUserProvider;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public TaskDto createTask(CreateTaskRequest request) {
        validateCreate(request);
        Task task = new Task();
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setPriority(request.priority() != null ? request.priority() : Priority.MEDIUM);
        task.setDueAt(request.dueAt());
        task.setStatus(Status.OPEN);
        task.setCreatedByUserId(currentUserUuid());
        if (request.assigneeUserId() != null) {
            assertUserExists(request.assigneeUserId());
            task.setAssigneeUserId(request.assigneeUserId());
        }
        Task saved = taskRepository.save(task);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskDto getTask(UUID id) {
        Task task = findTask(id);
        return toDto(task);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskDto> listMyTasks(String assignee, String status, Pageable pageable) {
        UUID assigneeId = resolveAssignee(assignee);
        Page<Task> page;
        if (status != null && !status.isBlank()) {
            Status st = parseStatus(status);
            page = taskRepository.findByAssigneeUserIdAndStatus(assigneeId, st, pageable);
        } else {
            page = taskRepository.findByAssigneeUserId(assigneeId, pageable);
        }
        return page.map(this::toDto);
    }

    @Override
    public TaskDto updateTask(UUID id, UpdateTaskRequest request) {
        Task task = findTask(id);
        if (request.title() != null) task.setTitle(request.title());
        if (request.description() != null) task.setDescription(request.description());
        if (request.priority() != null) task.setPriority(request.priority());
        if (request.dueAt() != null) task.setDueAt(request.dueAt());
        if (request.assigneeUserId() != null) {
            assertUserExists(request.assigneeUserId());
            task.setAssigneeUserId(request.assigneeUserId());
        }
        Task saved = taskRepository.save(task);
        return toDto(saved);
    }

    @Override
    public TaskDto updateStatus(UUID id, UpdateTaskStatusRequest request) {
        if (request == null || request.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        Task task = findTask(id);
        if (!isValidTransition(task.getStatus(), request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status transition");
        }
        task.setStatus(request.status());
        if (request.status() == Status.DONE) {
            task.setCompletedAt(java.time.OffsetDateTime.now());
            eventPublisher.publishEvent(new TaskCompletedEvent(task.getId()));
        }
        if (request.status() == Status.OPEN) {
            task.setCompletedAt(null);
        }
        Task saved = taskRepository.save(task);
        if (shouldEmitReadyForMo(task)) {
            eventPublisher.publishEvent(new TaskReadyForMoEvent(task.getId()));
        }
        return toDto(saved);
    }

    @Override
    public TaskLinkDto addLink(UUID taskId, CreateTaskLinkRequest request) {
        Task task = findTask(taskId);
        TaskLink link = new TaskLink();
        link.setTaskId(task.getId());
        link.setRefType(request.refType());
        link.setRefId(request.refId());
        link.setRefMeta(request.refMeta());
        TaskLink saved = taskLinkRepository.save(link);
        return new TaskLinkDto(saved);
    }

    @Override
    public void deleteLink(UUID taskId, UUID linkId) {
        TaskLink link = taskLinkRepository.findById(linkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found"));
        if (!link.getTaskId().equals(taskId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link does not belong to task");
        }
        taskLinkRepository.delete(link);
    }

    private Task findTask(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private UUID currentUserUuid() {
        return UUID.fromString(currentUserProvider.getCurrentUserId());
    }

    private void validateCreate(CreateTaskRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is required");
        }
        if (request.assigneeUserId() != null) {
            assertUserExists(request.assigneeUserId());
        }
    }

    private void assertUserExists(UUID userId) {
        if (!userPort.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee not found");
        }
    }

    private UUID resolveAssignee(String assignee) {
        if (assignee == null || assignee.isBlank() || "me".equalsIgnoreCase(assignee)) {
            return currentUserUuid();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only assignee=me is supported");
    }

    private Status parseStatus(String status) {
        try {
            return Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
        }
    }

    private boolean isValidTransition(Status current, Status target) {
        return switch (current) {
            case OPEN -> target == Status.IN_PROGRESS || target == Status.CANCELED;
            case IN_PROGRESS -> target == Status.DONE || target == Status.CANCELED;
            case DONE, CANCELED -> false;
        };
    }

    private boolean shouldEmitReadyForMo(Task task) {
        // placeholder hook; extend with conditions later
        return false;
    }

    private TaskDto toDto(Task task) {
        List<TaskLinkDto> links = taskLinkRepository.findByTaskId(task.getId()).stream()
                .map(TaskLinkDto::new)
                .collect(Collectors.toList());
        return new TaskDto(task, links);
    }
}


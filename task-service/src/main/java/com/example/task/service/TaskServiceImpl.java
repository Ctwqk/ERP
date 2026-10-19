package com.example.task.service;

import com.example.common.events.TaskCompletedEvent;
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
import com.example.task.ports.CorePort;
import com.example.task.ports.InventoryPort;
import com.example.task.ports.UserPort;
import com.example.task.repository.TaskLinkRepository;
import com.example.task.repository.TaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
    private final ObjectMapper objectMapper;

    public TaskServiceImpl(TaskRepository taskRepository,
            TaskLinkRepository taskLinkRepository,
            UserPort userPort,
            InventoryPort inventoryPort,
            CorePort corePort,
            CurrentUserProvider currentUserProvider,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.taskLinkRepository = taskLinkRepository;
        this.userPort = userPort;
        this.inventoryPort = inventoryPort;
        this.corePort = corePort;
        this.currentUserProvider = currentUserProvider;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public TaskDto createTask(CreateTaskRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is required");
        }
        validatePrerequisite(request.prerequisiteItemId(), request.prerequisiteItemQuantity());
        if (request.assigneeUserId() != null) {
            assertUserExists(request.assigneeUserId());
        }

        Task task = new Task();
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setPriority(request.priority() != null ? request.priority() : Priority.MEDIUM);
        task.setDueAt(request.dueAt());
        task.setPrerequisiteItemId(request.prerequisiteItemId());
        task.setPrerequisiteItemQuantity(request.prerequisiteItemQuantity());
        task.setStatus(Status.OPEN);
        task.setCreatedByUserId(currentUserProvider.getCurrentUserId());
        task.setAssigneeUserId(request.assigneeUserId());
        return toDto(taskRepository.save(task));
    }

    @Override
    @Transactional(readOnly = true)
    public TaskDto getTask(UUID id) {
        return toDto(findTask(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskDto> listMyTasks(String assignee, String status, Pageable pageable) {
        UUID assigneeId = resolveAssignee(assignee);
        Page<Task> page;
        if (status != null && !status.isBlank()) {
            page = taskRepository.findByAssigneeUserIdAndStatus(assigneeId, parseStatus(status), pageable);
        } else {
            page = taskRepository.findByAssigneeUserId(assigneeId, pageable);
        }
        return page.map(this::toDto);
    }

    @Override
    public TaskDto updateTask(UUID id, UpdateTaskRequest request) {
        Task task = findTask(id);
        if (task.getStatus() == Status.DONE || task.getStatus() == Status.CANCELED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A " + task.getStatus() + " task cannot be edited");
        }
        UUID prerequisiteItem = request.prerequisiteItemId() != null
                ? request.prerequisiteItemId() : task.getPrerequisiteItemId();
        BigDecimal prerequisiteQty = request.prerequisiteItemQuantity() != null
                ? request.prerequisiteItemQuantity() : task.getPrerequisiteItemQuantity();
        if (request.prerequisiteItemId() != null || request.prerequisiteItemQuantity() != null) {
            validatePrerequisite(prerequisiteItem, prerequisiteQty);
        }
        if (request.assigneeUserId() != null) {
            assertUserExists(request.assigneeUserId());
            task.setAssigneeUserId(request.assigneeUserId());
        }
        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title must not be blank");
            }
            task.setTitle(request.title());
        }
        if (request.description() != null) task.setDescription(request.description());
        if (request.priority() != null) task.setPriority(request.priority());
        if (request.dueAt() != null) task.setDueAt(request.dueAt());
        task.setPrerequisiteItemId(prerequisiteItem);
        task.setPrerequisiteItemQuantity(prerequisiteQty);
        return toDto(taskRepository.save(task));
    }

    @Override
    public TaskDto updateStatus(UUID id, UpdateTaskStatusRequest request) {
        if (request == null || request.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        Task task = findTask(id);
        Status target = request.status();
        if (!isValidTransition(task.getStatus(), target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid status transition " + task.getStatus() + " -> " + target);
        }
        if (target == Status.IN_PROGRESS) {
            assertPrerequisiteStockAvailable(task);
        }
        task.setStatus(target);
        if (target == Status.DONE) {
            task.setCompletedAt(OffsetDateTime.now());
        }
        Task saved = taskRepository.save(task);
        if (target == Status.DONE) {
            // published to RabbitMQ by TaskEventPublisher once this transaction commits
            eventPublisher.publishEvent(new TaskCompletedEvent(saved.getId(), saved.getTitle(),
                    saved.getCreatedByUserId(), saved.getAssigneeUserId(), saved.getCompletedAt()));
        }
        return toDto(saved);
    }

    @Override
    public TaskLinkDto addLink(UUID taskId, CreateTaskLinkRequest request) {
        Task task = findTask(taskId);
        validateReference(request.refType(), request.refId());
        String refMeta = normalizeJson(request.refMeta());
        TaskLink link = new TaskLink();
        link.setTaskId(task.getId());
        link.setRefType(request.refType());
        link.setRefId(request.refId());
        link.setRefMeta(refMeta);
        return new TaskLinkDto(taskLinkRepository.save(link));
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

    private void validatePrerequisite(UUID itemId, BigDecimal quantity) {
        if (itemId == null && quantity == null) {
            return;
        }
        if (itemId == null || quantity == null || quantity.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "prerequisiteItemId and a positive prerequisiteItemQuantity must be given together");
        }
        if (!corePort.existsItem(itemId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prerequisite item not found");
        }
    }

    private void assertPrerequisiteStockAvailable(Task task) {
        if (task.getPrerequisiteItemId() == null || task.getPrerequisiteItemQuantity() == null) {
            return;
        }
        if (!inventoryPort.checkStockAvailable(task.getPrerequisiteItemId(), task.getPrerequisiteItemQuantity())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Prerequisite not met: not enough stock of item " + task.getPrerequisiteItemId()
                            + " (need " + task.getPrerequisiteItemQuantity().stripTrailingZeros().toPlainString()
                            + ")");
        }
    }

    private void validateReference(RefType type, String refId) {
        switch (type) {
            case USER -> assertExists(userPort.existsById(parseUuid(refId)), "User");
            case ITEM -> assertExists(corePort.existsItem(parseUuid(refId)), "Item");
            case ORDER -> assertExists(corePort.existsOrder(parseUuid(refId)), "Order");
            case DOCUMENT -> assertExists(corePort.existsDocument(parseUuid(refId)), "Document");
            case INVENTORY, MO -> {
                // opaque references: nothing to verify
            }
        }
    }

    private static void assertExists(boolean exists, String what) {
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, what + " reference not found");
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refId must be a UUID for this link type");
        }
    }

    private String normalizeJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refMeta must be a JSON object");
            }
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refMeta must be valid JSON");
        }
    }

    private void assertUserExists(UUID userId) {
        if (!userPort.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee not found");
        }
    }

    private UUID resolveAssignee(String assignee) {
        if (assignee == null || assignee.isBlank() || "me".equalsIgnoreCase(assignee)) {
            return currentUserProvider.getCurrentUserId();
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

    static boolean isValidTransition(Status current, Status target) {
        return switch (current) {
            case OPEN -> target == Status.IN_PROGRESS || target == Status.CANCELED;
            case IN_PROGRESS -> target == Status.DONE || target == Status.CANCELED;
            case DONE, CANCELED -> false;
        };
    }

    private TaskDto toDto(Task task) {
        List<TaskLinkDto> links = taskLinkRepository.findByTaskId(task.getId()).stream()
                .map(TaskLinkDto::new)
                .collect(Collectors.toList());
        return new TaskDto(task, links);
    }
}

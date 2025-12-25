package com.example.task.controller;

import com.example.task.dto.CreateTaskLinkRequest;
import com.example.task.dto.CreateTaskRequest;
import com.example.task.dto.TaskDto;
import com.example.task.dto.TaskLinkDto;
import com.example.task.dto.UpdateTaskRequest;
import com.example.task.dto.UpdateTaskStatusRequest;
import com.example.task.service.TaskService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDto create(@Valid @RequestBody CreateTaskRequest request) {
        return taskService.createTask(request);
    }

    @GetMapping("/{id}")
    public TaskDto getById(@PathVariable UUID id) {
        return taskService.getTask(id);
    }

    @GetMapping
    public Page<TaskDto> list(@RequestParam(name = "assignee", defaultValue = "me") String assignee,
            @RequestParam(name = "status", required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return taskService.listMyTasks(assignee, status, pageable);
    }

    @PatchMapping("/{id}")
    public TaskDto update(@PathVariable UUID id, @RequestBody UpdateTaskRequest request) {
        return taskService.updateTask(id, request);
    }

    @PatchMapping("/{id}/status")
    public TaskDto updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateTaskStatusRequest request) {
        return taskService.updateStatus(id, request);
    }

    @PostMapping("/{id}/links")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskLinkDto addLink(@PathVariable UUID id, @Valid @RequestBody CreateTaskLinkRequest request) {
        return taskService.addLink(id, request);
    }

    @DeleteMapping("/{id}/links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLink(@PathVariable UUID id, @PathVariable UUID linkId) {
        taskService.deleteLink(id, linkId);
    }
}


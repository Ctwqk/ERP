package com.example.task.service;

import com.example.task.dto.CreateTaskLinkRequest;
import com.example.task.dto.CreateTaskRequest;
import com.example.task.dto.TaskDto;
import com.example.task.dto.TaskLinkDto;
import com.example.task.dto.UpdateTaskRequest;
import com.example.task.dto.UpdateTaskStatusRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskService {
    TaskDto createTask(CreateTaskRequest request);

    TaskDto getTask(UUID id);

    Page<TaskDto> listMyTasks(String assignee, String status, Pageable pageable);

    TaskDto updateTask(UUID id, UpdateTaskRequest request);

    TaskDto updateStatus(UUID id, UpdateTaskStatusRequest request);

    TaskLinkDto addLink(UUID taskId, CreateTaskLinkRequest request);

    void deleteLink(UUID taskId, UUID linkId);
}

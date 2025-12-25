package com.example.task.repository;

import com.example.task.domain.Task;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    Page<Task> findByAssigneeUserId(UUID assigneeUserId, Pageable pageable);
    Page<Task> findByAssigneeUserIdAndStatus(UUID assigneeUserId, Task.Status status, Pageable pageable);
}


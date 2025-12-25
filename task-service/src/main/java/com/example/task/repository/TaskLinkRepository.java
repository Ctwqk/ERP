package com.example.task.repository;

import com.example.task.domain.TaskLink;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskLinkRepository extends JpaRepository<TaskLink, UUID> {
    List<TaskLink> findByTaskId(UUID taskId);
}


package com.example.task.repository;

import com.example.task.domain.MoTaskLink;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoTaskLinkRepository extends JpaRepository<MoTaskLink, UUID> {
    List<MoTaskLink> findByMoId(UUID moId);
    List<MoTaskLink> findByTaskId(UUID taskId);
}


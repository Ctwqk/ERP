package com.example.task.ports;

import java.util.UUID;

public interface UserPort {
    boolean existsById(UUID userId);
}


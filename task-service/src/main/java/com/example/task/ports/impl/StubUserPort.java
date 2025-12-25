package com.example.task.ports.impl;

import com.example.task.ports.UserPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StubUserPort implements UserPort {
    @Override
    public boolean existsById(UUID userId) {
        // TODO: replace with HTTP client to user-service
        return true;
    }
}


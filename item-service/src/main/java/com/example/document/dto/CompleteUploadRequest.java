package com.example.document.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CompleteUploadRequest(@NotNull UUID documentId) {
}

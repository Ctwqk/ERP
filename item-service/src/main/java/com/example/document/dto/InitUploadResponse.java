package com.example.document.dto;

import java.util.UUID;
import java.time.OffsetDateTime;

public record InitUploadResponse(UUID documentId, String bucket, String objectKey, String uploadUrl,
        OffsetDateTime expiresAt) {

}

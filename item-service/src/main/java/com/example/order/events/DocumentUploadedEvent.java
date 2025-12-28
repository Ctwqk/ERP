package com.example.order.events;

public record DocumentUploadedEvent(
        String documentId,
        String linkType,
        String linkId,
        String fileType
) {
}


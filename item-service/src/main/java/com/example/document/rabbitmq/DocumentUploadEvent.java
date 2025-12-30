package com.example.document.rabbitmq;

import java.util.List;
import java.util.UUID;

public record DocumentUploadEvent(List<UUID> documentIds, String documentType, UUID linkId, List<String> urls) {

}

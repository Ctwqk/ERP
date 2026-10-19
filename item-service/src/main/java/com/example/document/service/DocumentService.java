package com.example.document.service;

import com.example.document.dto.DocumentDto;
import java.util.List;
import java.util.UUID;

public interface DocumentService {
    DocumentDto createDocument(DocumentDto documentDto);

    DocumentDto getDocumentById(UUID id);

    DocumentDto updateDocument(DocumentDto documentDto);

    void deleteDocument(UUID id);

    /** Documents attached to the given item, or all documents when {@code itemId} is null. */
    List<DocumentDto> getDocumentsByItemId(UUID itemId);

    /** Short-lived presigned download URL; only for {@code APPROVED} (fully uploaded) documents. */
    String downloadUrl(UUID documentId);
}

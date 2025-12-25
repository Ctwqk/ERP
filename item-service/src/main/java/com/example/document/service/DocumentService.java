package com.example.document.service;

import java.util.UUID;

import com.example.document.dto.DocumentDto;

import java.util.List;

public interface DocumentService {
    DocumentDto createDocument(DocumentDto documentDto);

    DocumentDto getDocumentById(UUID id);

    DocumentDto updateDocument(DocumentDto documentDto);

    void deleteDocument(UUID id);

    List<DocumentDto> getDocumentsByItemId(UUID itemId);
}

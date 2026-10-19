package com.example.document.controller;

import com.example.common.security.JwtUsers;
import com.example.document.dto.CompleteUploadRequest;
import com.example.document.dto.CompleteUploadResponse;
import com.example.document.dto.DocumentDto;
import com.example.document.dto.InitUploadRequest;
import com.example.document.dto.InitUploadResponse;
import com.example.document.service.DocumentService;
import com.example.document.service.DocumentUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Document metadata plus direct-to-MinIO uploads via presigned URLs")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentUploadService uploadService;

    public DocumentController(DocumentService documentService, DocumentUploadService uploadService) {
        this.documentService = documentService;
        this.uploadService = uploadService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register document metadata for an object that already exists in storage")
    public DocumentDto create(@RequestBody DocumentDto dto) {
        return documentService.createDocument(dto);
    }

    @GetMapping("/{id}")
    public DocumentDto getById(@PathVariable UUID id) {
        return documentService.getDocumentById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Patch a document; null fields are left unchanged")
    public DocumentDto update(@PathVariable UUID id, @RequestBody DocumentDto dto) {
        return documentService.updateDocument(new DocumentDto(id, dto.docType(), dto.title(), dto.originalFilename(),
                dto.mimeType(), dto.sizeBytes(), dto.checksumSha256(), dto.classification(), dto.revision(),
                dto.status(), dto.effectiveFrom(), dto.effectiveTo(), dto.bucket(), dto.objectKey(), dto.versionId(),
                dto.etag(), dto.encryption(), dto.kmsKeyId(), dto.createdByUserId(), dto.createdAt(),
                dto.updatedAt()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        documentService.deleteDocument(id);
    }

    @GetMapping
    @Operation(summary = "List documents, optionally only those attached to an item")
    public List<DocumentDto> listByItem(@RequestParam(required = false) UUID itemId) {
        return documentService.getDocumentsByItemId(itemId);
    }

    @PostMapping("/{linkId}/images:init")
    @Operation(summary = "Step 1 of an upload: register a DRAFT document and get a presigned PUT URL",
            description = "Despite the path, any file type works. `linkId` is the item id (linkType=ITEM, default) or "
                    + "an order id (linkType=ORDER). Upload the bytes with `PUT <uploadUrl>`, then call complete.")
    public InitUploadResponse initUpload(@PathVariable UUID linkId, @Valid @RequestBody InitUploadRequest request,
            Authentication authentication) {
        return uploadService.init(linkId, request, JwtUsers.userId(authentication));
    }

    @PostMapping("/{linkId}/images:complete")
    @Operation(summary = "Step 2 of an upload: verify the stored object and publish the upload event",
            description = "Publishes `document.file.upload` on RabbitMQ. An uploaded ORDER spreadsheet (.xlsx) is "
                    + "imported as an order by item-service; notification-service tells the uploader.")
    public CompleteUploadResponse complete(@PathVariable UUID linkId, @Valid @RequestBody CompleteUploadRequest req,
            Authentication authentication) {
        return uploadService.complete(linkId, req, JwtUsers.userId(authentication),
                JwtUsers.subject(authentication));
    }

    @GetMapping("/documents/{documentId}:download-url")
    @Operation(summary = "Presigned GET URL (5 minutes) for an approved document")
    public Map<String, String> downloadUrl(@PathVariable UUID documentId) {
        return Map.of("url", documentService.downloadUrl(documentId));
    }
}

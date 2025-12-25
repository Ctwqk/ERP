package com.example.document.controller;

import com.example.document.dto.DocumentDto;
import com.example.document.dto.InitUploadRequest;
import com.example.document.dto.InitUploadResponse;
import com.example.document.dto.CompleteUploadRequest;
import com.example.document.dto.CompleteUploadResponse;
import com.example.document.service.DocumentService;
import com.example.document.service.ItemImageService;
import com.example.document.repository.DocumentRepository;
import com.example.document.domain.Document;
import com.example.document.domain.Document.Status;
import com.example.document.dto.MinioProps;
import io.minio.MinioClient;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final ItemImageService itemImageService;
    private final DocumentRepository docRepo;
    private final MinioClient minio;
    private final MinioProps props;

    public DocumentController(DocumentService documentService,
            ItemImageService itemImageService,
            DocumentRepository docRepo,
            MinioClient minio,
            MinioProps props) {
        this.documentService = documentService;
        this.itemImageService = itemImageService;
        this.docRepo = docRepo;
        this.minio = minio;
        this.props = props;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto create(@RequestBody DocumentDto dto) {
        return documentService.createDocument(dto);
    }

    @GetMapping("/{id}")
    public DocumentDto getById(@PathVariable UUID id) {
        return documentService.getDocumentById(id);
    }

    @PutMapping("/{id}")
    public DocumentDto update(@PathVariable UUID id, @RequestBody DocumentDto dto) {
        dto = new DocumentDto(
                id,
                dto.docType(),
                dto.title(),
                dto.originalFilename(),
                dto.mimeType(),
                dto.sizeBytes(),
                dto.checksumSha256(),
                dto.classification(),
                dto.revision(),
                dto.status(),
                dto.effectiveFrom(),
                dto.effectiveTo(),
                dto.bucket(),
                dto.objectKey(),
                dto.versionId(),
                dto.etag(),
                dto.encryption(),
                dto.kmsKeyId(),
                dto.createdByUserId(),
                dto.createdAt(),
                dto.updatedAt());
        return documentService.updateDocument(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        documentService.deleteDocument(id);
    }

    @GetMapping
    public List<DocumentDto> listByItem(@RequestParam(required = false) UUID itemId) {

        if (itemId != null) {
            return documentService.getDocumentsByItemId(itemId);
        }
        return documentService.getDocumentsByItemId(null);
    }

    @PostMapping("/{itemId}/images:init")
    public InitUploadResponse initUpload(@PathVariable UUID itemId, @RequestBody InitUploadRequest request)
            throws Exception {
        return itemImageService.init(itemId, request);
    }

    @PostMapping("/{linkId}/images:complete")
    public CompleteUploadResponse complete(@PathVariable UUID linkId, @RequestBody CompleteUploadRequest req)
            throws Exception {
        return itemImageService.complete(linkId, req);
    }

    @GetMapping("/documents/{documentId}:download-url")
    public Map<String, String> downloadUrl(@PathVariable UUID documentId) throws Exception {
        Document doc = docRepo.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (doc.getStatus() != Status.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not ready");
        }

        String url = minio.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(doc.getBucket())
                        .object(doc.getObjectKey())
                        .expiry(5 * 60)
                        .build());
        url = rewriteToPublicEndpoint(url, props.publicEndpoint());

        return Map.of("url", url);
    }

    private static String rewriteToPublicEndpoint(String url, String publicEndpoint) {
        if (publicEndpoint == null || publicEndpoint.isBlank()) {
            return url;
        }
        return url.replaceFirst("^https?://[^/]+", publicEndpoint);
    }
}

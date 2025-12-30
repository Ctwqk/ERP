package com.example.document.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.document.dto.InitUploadRequest;
import com.example.document.dto.InitUploadResponse;
import com.example.document.dto.CompleteUploadRequest;
import com.example.document.dto.CompleteUploadResponse;
import com.example.document.dto.MinioProps;
import com.example.document.repository.DocumentRepository;
import com.example.document.repository.DocumentLinkRepository;
import com.example.document.domain.Document;
import com.example.document.domain.Document.Status;
import com.example.document.domain.DocumentLink;
import io.minio.StatObjectResponse;
import io.minio.StatObjectArgs;
import io.minio.MinioClient;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import com.example.document.rabbitmq.DocumentUploadEvent;
import com.example.document.rabbitmq.DocumentEventPublisher;

@Service
public class ItemImageService {
    private final MinioClient minio;
    private final MinioProps props;
    private final DocumentRepository docRepo;
    private final DocumentLinkRepository docLinkRepo;
    private final DocumentEventPublisher documentEventPublisher;

    public ItemImageService(MinioClient minio, MinioProps props,
            DocumentRepository docRepo, DocumentLinkRepository docLinkRepo,
            DocumentEventPublisher documentEventPublisher) {
        this.minio = minio;
        this.props = props;
        this.docRepo = docRepo;
        this.docLinkRepo = docLinkRepo;
        this.documentEventPublisher = documentEventPublisher;
    }

    @Transactional
    public InitUploadResponse init(UUID itemId, InitUploadRequest req) throws Exception {
        // if (req.contentType() == null || !req.contentType().startsWith("image/")) {
        // throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image/* is
        // allowed");
        // }
        if (req.sizeBytes() == null || req.sizeBytes() <= 0 || req.sizeBytes() > 100 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid size");
        }

        UUID docId = UUID.randomUUID();
        String objectKey = "items/%s/images/%s".formatted(itemId, docId);

        Document doc = new Document();
        doc.setId(docId);
        doc.setBucket(props.bucket());
        doc.setObjectKey(objectKey);
        doc.setMimeType(req.contentType());
        doc.setSizeBytes(req.sizeBytes());
        doc.setOriginalFilename(req.filename());
        doc.setStatus(Status.DRAFT);
        docRepo.save(doc);

        DocumentLink link = new DocumentLink();
        link.setLinkId(itemId);
        link.setDocumentId(docId);
        DocumentLink.LinkType linkType = resolveLinkType(req.linkType());
        DocumentLink.Purpose purpose = resolvePurpose(req.purpose());
        link.setLinkType(linkType);
        link.setPurpose(purpose);
        docLinkRepo.save(link);

        int expiresSeconds = 10 * 60;
        String uploadUrl = minio.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(props.bucket())
                        .object(objectKey)
                        .expiry(expiresSeconds)
                        .build());

        uploadUrl = rewriteToPublicEndpoint(uploadUrl, props.publicEndpoint());

        return new InitUploadResponse(
                docId,
                props.bucket(),
                objectKey,
                uploadUrl,
                OffsetDateTime.now().plusSeconds(expiresSeconds));
    }

    private static DocumentLink.LinkType resolveLinkType(String linkType) {
        if (linkType == null || linkType.isBlank()) {
            return DocumentLink.LinkType.ITEM;
        }
        try {
            return DocumentLink.LinkType.valueOf(linkType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DocumentLink.LinkType.ITEM;
        }
    }

    private static DocumentLink.Purpose resolvePurpose(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return DocumentLink.Purpose.PRIMARY;
        }
        try {
            return DocumentLink.Purpose.valueOf(purpose.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DocumentLink.Purpose.PRIMARY;
        }
    }

    private static String rewriteToPublicEndpoint(String url, String publicEndpoint) {
        if (publicEndpoint == null || publicEndpoint.isBlank())
            return url;

        return url.replaceFirst("^https?://[^/]+", publicEndpoint);
    }

    @Transactional
    public CompleteUploadResponse complete(UUID linkId, CompleteUploadRequest req) throws Exception {
        Document doc = docRepo.findById(req.documentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        if (!docLinkRepo.existsByDocumentIdAndLinkId(doc.getId(), linkId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your document");
        }

        StatObjectResponse stat = minio.statObject(
                StatObjectArgs.builder()
                        .bucket(doc.getBucket())
                        .object(doc.getObjectKey())
                        .build());

        if (doc.getSizeBytes() != null && stat.size() != doc.getSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size mismatch");
        }

        doc.setStatus(Status.APPROVED);

        docRepo.save(doc);
        DocumentUploadEvent event = new DocumentUploadEvent(List.of(doc.getId()), doc.getDocType().name(), linkId,
                null);
        documentEventPublisher.publishUploaded(event);
        return new CompleteUploadResponse(true);
    }

}

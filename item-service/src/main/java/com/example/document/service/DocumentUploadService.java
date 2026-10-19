package com.example.document.service;

import com.example.common.events.DocumentUploadEvent;
import com.example.document.domain.Document;
import com.example.document.domain.Document.Classification;
import com.example.document.domain.Document.DocType;
import com.example.document.domain.Document.Encryption;
import com.example.document.domain.Document.Status;
import com.example.document.domain.DocumentLink;
import com.example.document.domain.DocumentLink.LinkType;
import com.example.document.domain.DocumentLink.Purpose;
import com.example.document.dto.CompleteUploadRequest;
import com.example.document.dto.CompleteUploadResponse;
import com.example.document.dto.InitUploadRequest;
import com.example.document.dto.InitUploadResponse;
import com.example.document.rabbitmq.DocumentEventPublisher;
import com.example.document.repository.DocumentLinkRepository;
import com.example.document.repository.DocumentRepository;
import com.example.item.repository.ItemRepository;
import io.minio.StatObjectResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Two-step direct-to-storage upload: {@link #init} registers a DRAFT document and hands out a presigned PUT URL, the
 * client uploads the bytes straight to MinIO, then {@link #complete} verifies the object, approves the document and
 * publishes a {@link DocumentUploadEvent}.
 */
@Service
public class DocumentUploadService {

    static final long MAX_SIZE_BYTES = 100L * 1024 * 1024;
    static final int UPLOAD_URL_SECONDS = 10 * 60;

    private final DocumentStorage storage;
    private final DocumentRepository docRepo;
    private final DocumentLinkRepository docLinkRepo;
    private final ItemRepository itemRepo;
    private final DocumentEventPublisher documentEventPublisher;

    public DocumentUploadService(DocumentStorage storage, DocumentRepository docRepo,
            DocumentLinkRepository docLinkRepo, ItemRepository itemRepo,
            DocumentEventPublisher documentEventPublisher) {
        this.storage = storage;
        this.docRepo = docRepo;
        this.docLinkRepo = docLinkRepo;
        this.itemRepo = itemRepo;
        this.documentEventPublisher = documentEventPublisher;
    }

    @Transactional
    public InitUploadResponse init(UUID linkId, InitUploadRequest req, UUID userId) {
        if (req.sizeBytes() == null || req.sizeBytes() <= 0 || req.sizeBytes() > MAX_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sizeBytes must be between 1 and " + MAX_SIZE_BYTES);
        }
        LinkType linkType = resolveLinkType(req.linkType());
        if (linkType == LinkType.ITEM && !itemRepo.existsById(linkId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        }
        storage.ensureBucket();

        UUID docId = UUID.randomUUID();
        Document doc = new Document();
        doc.setDocType(req.docType() != null ? req.docType()
                : linkType == LinkType.ORDER ? DocType.ORDER : DocType.ITEM);
        doc.setTitle(req.filename());
        doc.setOriginalFilename(req.filename());
        doc.setMimeType(req.contentType());
        doc.setSizeBytes(req.sizeBytes());
        doc.setClassification(Classification.INTERNAL);
        doc.setRevision("A");
        doc.setStatus(Status.DRAFT);
        doc.setBucket(storage.bucket());
        doc.setObjectKey("%ss/%s/%s".formatted(linkType.name().toLowerCase(), linkId, docId));
        doc.setEncryption(Encryption.NONE);
        doc.setCreatedByUserId(userId);
        doc = docRepo.save(doc);

        DocumentLink link = new DocumentLink();
        link.setLinkId(linkId);
        link.setDocumentId(doc.getId());
        link.setLinkType(linkType);
        link.setPurpose(resolvePurpose(req.purpose()));
        docLinkRepo.save(link);

        String uploadUrl = storage.presignedPutUrl(doc.getBucket(), doc.getObjectKey(), UPLOAD_URL_SECONDS);
        return new InitUploadResponse(doc.getId(), doc.getBucket(), doc.getObjectKey(), uploadUrl,
                OffsetDateTime.now().plusSeconds(UPLOAD_URL_SECONDS));
    }

    @Transactional
    public CompleteUploadResponse complete(UUID linkId, CompleteUploadRequest req, UUID userId, String username) {
        Document doc = docRepo.findById(req.documentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        if (!docLinkRepo.existsByDocumentIdAndLinkId(doc.getId(), linkId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Document is not linked to " + linkId);
        }
        if (doc.getStatus() != Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload already completed");
        }

        StatObjectResponse stat = storage.stat(doc.getBucket(), doc.getObjectKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No object found in storage - upload the file to the presigned URL first"));
        if (doc.getSizeBytes() != null && stat.size() != doc.getSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Size mismatch: declared %d bytes, stored %d bytes".formatted(doc.getSizeBytes(), stat.size()));
        }

        doc.setStatus(Status.APPROVED);
        doc.setEtag(stat.etag());
        docRepo.save(doc);
        documentEventPublisher.publishUploaded(new DocumentUploadEvent(List.of(doc.getId()), doc.getDocType().name(),
                linkId, null, userId, username));
        return new CompleteUploadResponse(true);
    }

    private static LinkType resolveLinkType(String linkType) {
        if (linkType == null || linkType.isBlank()) {
            return LinkType.ITEM;
        }
        try {
            return LinkType.valueOf(linkType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "linkType must be ITEM or ORDER");
        }
    }

    private static Purpose resolvePurpose(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return Purpose.PRIMARY;
        }
        try {
            return Purpose.valueOf(purpose.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "purpose must be PRIMARY or REFERENCE");
        }
    }
}

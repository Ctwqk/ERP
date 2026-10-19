package com.example.document.service;

import com.example.document.domain.Document;
import com.example.document.domain.Document.Classification;
import com.example.document.domain.Document.Encryption;
import com.example.document.domain.Document.Status;
import com.example.document.domain.DocumentLink;
import com.example.document.domain.DocumentLink.LinkType;
import com.example.document.dto.DocumentDto;
import com.example.document.repository.DocumentLinkRepository;
import com.example.document.repository.DocumentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class DocumentServiceImpl implements DocumentService {

    static final int DOWNLOAD_URL_SECONDS = 5 * 60;

    private final DocumentRepository documentRepository;
    private final DocumentLinkRepository linkRepository;
    private final DocumentStorage storage;

    public DocumentServiceImpl(DocumentRepository documentRepository, DocumentLinkRepository linkRepository,
            DocumentStorage storage) {
        this.documentRepository = documentRepository;
        this.linkRepository = linkRepository;
        this.storage = storage;
    }

    @Override
    public DocumentDto createDocument(DocumentDto dto) {
        require(dto.docType() != null, "docType is required");
        require(dto.title() != null && !dto.title().isBlank(), "title is required");
        require(dto.sizeBytes() != null && dto.sizeBytes() >= 0, "sizeBytes is required");
        require(dto.bucket() != null && !dto.bucket().isBlank(), "bucket is required");
        require(dto.objectKey() != null && !dto.objectKey().isBlank(), "objectKey is required");
        return new DocumentDto(documentRepository.save(toEntity(dto)));
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDto getDocumentById(UUID id) {
        return new DocumentDto(find(id));
    }

    @Override
    public DocumentDto updateDocument(DocumentDto documentDto) {
        if (documentDto.id() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document id is required");
        }
        int updated = documentRepository.patchDocument(
                documentDto.id(),
                documentDto.docType() != null ? documentDto.docType().name() : null,
                documentDto.title(),
                documentDto.originalFilename(),
                documentDto.mimeType(),
                documentDto.sizeBytes(),
                documentDto.checksumSha256(),
                documentDto.classification() != null ? documentDto.classification().name() : null,
                documentDto.revision(),
                documentDto.status() != null ? documentDto.status().name() : null,
                documentDto.effectiveFrom(),
                documentDto.effectiveTo(),
                documentDto.bucket(),
                documentDto.objectKey(),
                documentDto.versionId(),
                documentDto.etag(),
                documentDto.encryption() != null ? documentDto.encryption().name() : null,
                documentDto.kmsKeyId(),
                documentDto.createdByUserId());
        if (updated == 0) {
            throw notFound();
        }
        return new DocumentDto(find(documentDto.id()));
    }

    @Override
    public void deleteDocument(UUID id) {
        Document doc = find(id);
        documentRepository.delete(doc);
        storage.remove(doc.getBucket(), doc.getObjectKey());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDto> getDocumentsByItemId(UUID itemId) {
        if (itemId == null) {
            return documentRepository.findAll().stream().map(DocumentDto::new).toList();
        }
        List<UUID> documentIds = linkRepository.findAllByLinkIdAndLinkType(itemId, LinkType.ITEM).stream()
                .map(DocumentLink::getDocumentId)
                .toList();
        return documentRepository.findAllById(documentIds).stream().map(DocumentDto::new).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String downloadUrl(UUID documentId) {
        Document doc = find(documentId);
        if (doc.getStatus() != Status.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document is not available yet (upload incomplete)");
        }
        return storage.presignedGetUrl(doc.getBucket(), doc.getObjectKey(), DOWNLOAD_URL_SECONDS);
    }

    private Document find(UUID id) {
        return documentRepository.findById(id).orElseThrow(DocumentServiceImpl::notFound);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private Document toEntity(DocumentDto dto) {
        Document doc = new Document();
        doc.setDocType(dto.docType());
        doc.setTitle(dto.title());
        doc.setOriginalFilename(dto.originalFilename());
        doc.setMimeType(dto.mimeType());
        doc.setSizeBytes(dto.sizeBytes());
        doc.setChecksumSha256(dto.checksumSha256());
        doc.setClassification(dto.classification() != null ? dto.classification() : Classification.INTERNAL);
        doc.setRevision(dto.revision() != null ? dto.revision() : "A");
        doc.setStatus(dto.status() != null ? dto.status() : Status.DRAFT);
        doc.setEffectiveFrom(dto.effectiveFrom());
        doc.setEffectiveTo(dto.effectiveTo());
        doc.setBucket(dto.bucket());
        doc.setObjectKey(dto.objectKey());
        doc.setVersionId(dto.versionId());
        doc.setEtag(dto.etag());
        doc.setEncryption(dto.encryption() != null ? dto.encryption() : Encryption.NONE);
        doc.setKmsKeyId(dto.kmsKeyId());
        doc.setCreatedByUserId(dto.createdByUserId());
        return doc;
    }
}

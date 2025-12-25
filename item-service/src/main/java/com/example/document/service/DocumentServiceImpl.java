package com.example.document.service;

import com.example.document.dto.DocumentDto;
import com.example.document.repository.DocumentRepository;
import com.example.document.domain.Document;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentServiceImpl(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public DocumentDto createDocument(DocumentDto documentDto) {
        Document saved = documentRepository.save(toEntity(documentDto));
        return new DocumentDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDto getDocumentById(UUID id) {
        Document doc = documentRepository.findById(id).orElseThrow(() -> new RuntimeException("Document not found"));
        return new DocumentDto(doc);
    }

    @Override
    public DocumentDto updateDocument(DocumentDto documentDto) {
        if (documentDto.id() == null) {
            throw new RuntimeException("Document ID is required");
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
            throw new RuntimeException("Document not found");
        }
        Document reloaded = documentRepository.findById(documentDto.id())
                .orElseThrow(() -> new RuntimeException("Document not found"));
        return new DocumentDto(reloaded);
    }

    @Override
    public void deleteDocument(UUID id) {
        documentRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDto> getDocumentsByItemId(UUID itemId) {
        // 当前未通过 document_link 过滤，直接返回全部；可按 link_type=ITEM 过滤后扩展
        return documentRepository.findAll().stream().map(DocumentDto::new).collect(Collectors.toList());
    }

    private Document toEntity(DocumentDto dto) {
        Document doc = new Document();
        if (dto.id() != null)
            doc.setId(dto.id());
        if (dto.docType() != null)
            doc.setDocType(dto.docType());
        if (dto.title() != null)
            doc.setTitle(dto.title());
        if (dto.originalFilename() != null)
            doc.setOriginalFilename(dto.originalFilename());
        if (dto.mimeType() != null)
            doc.setMimeType(dto.mimeType());
        if (dto.sizeBytes() != null)
            doc.setSizeBytes(dto.sizeBytes());
        if (dto.checksumSha256() != null)
            doc.setChecksumSha256(dto.checksumSha256());
        if (dto.classification() != null)
            doc.setClassification(dto.classification());
        if (dto.revision() != null)
            doc.setRevision(dto.revision());
        if (dto.status() != null)
            doc.setStatus(dto.status());
        if (dto.effectiveFrom() != null)
            doc.setEffectiveFrom(dto.effectiveFrom());
        if (dto.effectiveTo() != null)
            doc.setEffectiveTo(dto.effectiveTo());
        if (dto.bucket() != null)
            doc.setBucket(dto.bucket());
        if (dto.objectKey() != null)
            doc.setObjectKey(dto.objectKey());
        if (dto.versionId() != null)
            doc.setVersionId(dto.versionId());
        if (dto.etag() != null)
            doc.setEtag(dto.etag());
        if (dto.encryption() != null)
            doc.setEncryption(dto.encryption());
        if (dto.kmsKeyId() != null)
            doc.setKmsKeyId(dto.kmsKeyId());
        if (dto.createdByUserId() != null)
            doc.setCreatedByUserId(dto.createdByUserId());
        return doc;
    }
}

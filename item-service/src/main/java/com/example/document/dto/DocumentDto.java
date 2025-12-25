package com.example.document.dto;

import com.example.document.domain.Document;
import com.example.document.domain.Document.Classification;
import com.example.document.domain.Document.DocType;
import com.example.document.domain.Document.Encryption;
import com.example.document.domain.Document.Status;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentDto(
        UUID id,
        DocType docType,
        String title,
        String originalFilename,
        String mimeType,
        Long sizeBytes,
        String checksumSha256,
        Classification classification,
        String revision,
        Status status,
        OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo,
        String bucket,
        String objectKey,
        String versionId,
        String etag,
        Encryption encryption,
        String kmsKeyId,
        UUID createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public DocumentDto(Document doc) {
        this(doc.getId(),
                doc.getDocType(),
                doc.getTitle(),
                doc.getOriginalFilename(),
                doc.getMimeType(),
                doc.getSizeBytes(),
                doc.getChecksumSha256(),
                doc.getClassification(),
                doc.getRevision(),
                doc.getStatus(),
                doc.getEffectiveFrom(),
                doc.getEffectiveTo(),
                doc.getBucket(),
                doc.getObjectKey(),
                doc.getVersionId(),
                doc.getEtag(),
                doc.getEncryption(),
                doc.getKmsKeyId(),
                doc.getCreatedByUserId(),
                doc.getCreatedAt(),
                doc.getUpdatedAt());
    }
}



package com.example.document.repository;

import com.example.document.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findByChecksumSha256AndRevision(String checksumSha256, String revision);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            update document
               set doc_type = coalesce(:docType, doc_type),
                   title = coalesce(:title, title),
                   original_filename = coalesce(:originalFilename, original_filename),
                   mime_type = coalesce(:mimeType, mime_type),
                   size_bytes = coalesce(:sizeBytes, size_bytes),
                   checksum_sha256 = coalesce(:checksumSha256, checksum_sha256),
                   classification = coalesce(:classification, classification),
                   revision = coalesce(:revision, revision),
                   status = coalesce(:status, status),
                   effective_from = coalesce(:effectiveFrom, effective_from),
                   effective_to = coalesce(:effectiveTo, effective_to),
                   bucket = coalesce(:bucket, bucket),
                   object_key = coalesce(:objectKey, object_key),
                   version_id = coalesce(:versionId, version_id),
                   etag = coalesce(:etag, etag),
                   encryption = coalesce(:encryption, encryption),
                   kms_key_id = coalesce(:kmsKeyId, kms_key_id),
                   created_by_user_id = coalesce(:createdByUserId, created_by_user_id),
                   updated_at = now()
             where id = :id
            """, nativeQuery = true)
    int patchDocument(@Param("id") UUID id,
            @Param("docType") String docType,
            @Param("title") String title,
            @Param("originalFilename") String originalFilename,
            @Param("mimeType") String mimeType,
            @Param("sizeBytes") Long sizeBytes,
            @Param("checksumSha256") String checksumSha256,
            @Param("classification") String classification,
            @Param("revision") String revision,
            @Param("status") String status,
            @Param("effectiveFrom") OffsetDateTime effectiveFrom,
            @Param("effectiveTo") OffsetDateTime effectiveTo,
            @Param("bucket") String bucket,
            @Param("objectKey") String objectKey,
            @Param("versionId") String versionId,
            @Param("etag") String etag,
            @Param("encryption") String encryption,
            @Param("kmsKeyId") String kmsKeyId,
            @Param("createdByUserId") UUID createdByUserId);
}


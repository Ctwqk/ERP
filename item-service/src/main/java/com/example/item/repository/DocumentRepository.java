package com.example.item.repository;

import com.example.item.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findByChecksumSha256AndRevision(String checksumSha256, String revision);
}




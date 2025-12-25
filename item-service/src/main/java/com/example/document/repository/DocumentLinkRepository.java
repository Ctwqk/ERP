package com.example.document.repository;

import com.example.document.domain.DocumentLink;
import com.example.document.domain.DocumentLink.LinkType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentLinkRepository extends JpaRepository<DocumentLink, UUID> {

    List<DocumentLink> findAllByLinkIdAndLinkType(UUID linkId, LinkType linkType);

    List<DocumentLink> findAllByDocumentId(UUID documentId);

    Boolean existsByDocumentIdAndLinkId(UUID documentId, UUID linkId);
}

package com.example.document.repository;

import com.example.document.domain.DocumentLink;
import com.example.document.domain.DocumentLink.LinkType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentLinkRepository extends JpaRepository<DocumentLink, UUID> {

    List<DocumentLink> findAllByLinkIdAndLinkType(UUID linkId, LinkType linkType);

    List<DocumentLink> findAllByDocumentId(UUID documentId);

    boolean existsByDocumentIdAndLinkId(UUID documentId, UUID linkId);

    boolean existsByDocumentIdAndLinkIdAndLinkType(UUID documentId, UUID linkId, LinkType linkType);
}

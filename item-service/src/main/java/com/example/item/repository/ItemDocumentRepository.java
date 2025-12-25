package com.example.item.repository;

import com.example.item.domain.ItemDocument;
import com.example.item.domain.ItemDocument.ItemDocumentId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemDocumentRepository extends JpaRepository<ItemDocument, ItemDocumentId> {
    List<ItemDocument> findAllByItemId(UUID itemId);
}

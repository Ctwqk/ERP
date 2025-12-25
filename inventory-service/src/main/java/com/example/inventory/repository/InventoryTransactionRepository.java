package com.example.inventory.repository;

import com.example.inventory.domain.InventoryTransaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {
    List<InventoryTransaction> findByStockIdOrderByCreatedAtDesc(UUID stockId);
}



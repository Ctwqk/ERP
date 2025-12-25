package com.example.inventory.repository;

import com.example.inventory.domain.InventoryStock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, UUID> {
    Optional<InventoryStock> findByItemId(UUID itemId);
}



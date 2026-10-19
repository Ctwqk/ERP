package com.example.inventory.repository;

import com.example.inventory.domain.InventoryStock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, UUID> {

    Optional<InventoryStock> findByItemId(UUID itemId);

    /** Row-locks the stock so concurrent movements for the same item are serialized. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InventoryStock s where s.itemId = :itemId")
    Optional<InventoryStock> findByItemIdForUpdate(@Param("itemId") UUID itemId);

    /** Creates an empty stock row if none exists yet; safe under concurrent calls. */
    @Modifying
    @Query(value = """
            insert into inventory_stock (id, item_id, quantity, created_at, updated_at)
            values (:id, :itemId, 0, now(), now())
            on conflict (item_id) do nothing
            """, nativeQuery = true)
    int createIfAbsent(@Param("id") UUID id, @Param("itemId") UUID itemId);
}

package com.example.item.repository;

import com.example.item.domain.Bom;
import com.example.item.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BomRepository extends JpaRepository<Bom, UUID> {
    List<Bom> findAllByProductItem(Item productItem);

    boolean existsByProductItemIdAndRevisionAndStatus(UUID productItemId, String revision, Bom.BomStatus status);

    @Query("""
            select b from Bom b
             where (:productItemId is null or b.productItem.id = :productItemId)
               and (:revision is null or lower(b.revision) like lower(concat('%', cast(:revision as string), '%')))
               and (:status is null or b.status = :status)
            """)
    Page<Bom> search(@Param("productItemId") UUID productItemId,
            @Param("revision") String revision,
            @Param("status") Bom.BomStatus status,
            Pageable pageable);
}

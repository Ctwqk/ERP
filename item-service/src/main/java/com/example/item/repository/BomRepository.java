package com.example.item.repository;

import com.example.item.domain.Bom;
import com.example.item.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface BomRepository extends JpaRepository<Bom, UUID> {
    List<Bom> findAllByProductItem(Item productItem);

    boolean existsByProductItemIdAndRevisionAndStatus(UUID productItemId, String revision, Bom.BomStatus status);

    @Query(value = """
            select * from bom
             where (:productItemId is null or product_item_id = :productItemId)
               and (:revision is null or revision ilike concat('%', :revision, '%'))
               and (:status is null or status = :status)
            """, countQuery = """
            select count(*) from bom
             where (:productItemId is null or product_item_id = :productItemId)
               and (:revision is null or revision ilike concat('%', :revision, '%'))
               and (:status is null or status = :status)
            """, nativeQuery = true)
    Page<Bom> search(@Param("productItemId") UUID productItemId,
            @Param("revision") String revision,
            @Param("status") String status,
            Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            update bom
               set revision = coalesce(:revision, revision),
                   status = coalesce(:status, status),
                   effective_from = coalesce(:effectiveFrom, effective_from),
                   effective_to = coalesce(:effectiveTo, effective_to),
                   note = coalesce(:note, note),
                   updated_at = now()
             where id = :id
            """, nativeQuery = true)
    int patchBom(@Param("id") UUID id,
            @Param("revision") String revision,
            @Param("status") String status,
            @Param("effectiveFrom") OffsetDateTime effectiveFrom,
            @Param("effectiveTo") OffsetDateTime effectiveTo,
            @Param("note") String note);
}
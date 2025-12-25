package com.example.item.repository;

import com.example.item.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {
    Optional<Item> findBySkuCode(String skuCode);

    @Query(value = """
            select * from item
             where (:keyword is null or lower(name) like lower(concat('%', :keyword, '%'))
                    or lower(sku_code) like lower(concat('%', :keyword, '%')))
               and (:itemType is null or item_type = :itemType)
               and (:active is null or active = :active)
            """,
            countQuery = """
            select count(*) from item
             where (:keyword is null or lower(name) like lower(concat('%', :keyword, '%'))
                    or lower(sku_code) like lower(concat('%', :keyword, '%')))
               and (:itemType is null or item_type = :itemType)
               and (:active is null or active = :active)
            """,
            nativeQuery = true)
    Page<Item> search(@Param("keyword") String keyword,
                      @Param("itemType") String itemType,
                      @Param("active") Boolean active,
                      Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            update item
               set name = coalesce(:name, name),
                   sku_code = coalesce(:skuCode, sku_code),
                   item_type = coalesce(:itemType, item_type),
                   active = coalesce(:active, active),
                   description = coalesce(:description, description),
                   base_uom_id = coalesce(:baseUomId, base_uom_id),
                   updated_at = now()
             where id = :id
            """, nativeQuery = true)
    int patchItem(@Param("id") UUID id,
                  @Param("name") String name,
                  @Param("skuCode") String skuCode,
                  @Param("itemType") String itemType,
                  @Param("active") Boolean active,
                  @Param("description") String description,
                  @Param("baseUomId") UUID baseUomId);
}
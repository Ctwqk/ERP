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

    /** Optional filters; sorting by entity property names (e.g. {@code skuCode}) is supported through Pageable. */
    @Query("""
            select i from Item i
             where (:keyword is null or lower(i.name) like lower(concat('%', cast(:keyword as string), '%'))
                    or lower(i.skuCode) like lower(concat('%', cast(:keyword as string), '%')))
               and (:itemType is null or i.itemType = :itemType)
               and (:active is null or i.active = :active)
            """)
    Page<Item> search(@Param("keyword") String keyword,
                      @Param("itemType") Item.ItemType itemType,
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
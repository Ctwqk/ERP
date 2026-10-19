package com.example.order.integration;

import com.example.order.dto.ItemReference;
import java.util.List;
import java.util.UUID;

/** Resolves the items an order refers to (by id or SKU) into canonical id + SKU pairs. */
public interface ItemLookup {

    record ResolvedItem(UUID itemId, String sku) {
    }

    /**
     * @return one resolved item per reference, in the same order
     * @throws org.springframework.web.server.ResponseStatusException 400 if an item does not exist or is inactive
     */
    List<ResolvedItem> resolve(List<ItemReference> references);
}

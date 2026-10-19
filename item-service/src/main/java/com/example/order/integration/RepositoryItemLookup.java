package com.example.order.integration;

import com.example.item.domain.Item;
import com.example.item.repository.ItemRepository;
import com.example.order.dto.ItemReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Orders and items live in the same service, so lookups go straight to the item repository. */
@Component
public class RepositoryItemLookup implements ItemLookup {

    private final ItemRepository items;

    public RepositoryItemLookup(ItemRepository items) {
        this.items = items;
    }

    @Override
    public List<ResolvedItem> resolve(List<ItemReference> references) {
        if (references == null || references.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must contain at least one item");
        }
        List<ResolvedItem> resolved = new ArrayList<>(references.size());
        for (ItemReference ref : references) {
            if (ref == null || (ref.itemId() == null && (ref.sku() == null || ref.sku().isBlank()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each item requires itemId or sku");
            }
            Optional<Item> item = ref.itemId() != null
                    ? items.findById(ref.itemId())
                    : items.findBySkuCode(ref.sku().trim());
            Item found = item.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Item not found: " + (ref.itemId() != null ? ref.itemId() : ref.sku())));
            if (!Boolean.TRUE.equals(found.getActive())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Item " + found.getSkuCode() + " is inactive and cannot be ordered");
            }
            resolved.add(new ResolvedItem(found.getId(), found.getSkuCode()));
        }
        return resolved;
    }
}

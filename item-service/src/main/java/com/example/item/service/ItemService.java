
package com.example.item.service;

import com.example.item.dto.ItemDto;
import com.example.item.domain.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface ItemService {
    ItemDto createItem(ItemDto itemDto);

    ItemDto getItemById(UUID id);

    ItemDto getItemBySkuCode(String skuCode);

    List<ItemDto> getAllItems();

    Page<ItemDto> searchItems(String keyword, Item.ItemType itemType, Boolean active, Pageable pageable);

    ItemDto updateItem(ItemDto itemDto);

    void deleteItem(UUID id);

    List<UUID> getDocumentsByItemId(UUID id);
}

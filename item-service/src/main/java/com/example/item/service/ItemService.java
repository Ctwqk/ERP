
package com.example.item.service;

import com.example.item.dto.DocumentDto;
import com.example.item.dto.ItemDto;
import java.util.List;
import java.util.UUID;

public interface ItemService {
    ItemDto createItem(ItemDto itemDto);

    ItemDto getItemById(UUID id);

    ItemDto getItemBySkuCode(String skuCode);

    List<ItemDto> getAllItems();

    ItemDto updateItem(ItemDto itemDto);

    void deleteItem(UUID id);

    List<DocumentDto> getDocumentsByItemId(UUID id);
}

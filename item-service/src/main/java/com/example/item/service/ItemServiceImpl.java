package com.example.item.service;

import com.example.item.domain.Item;
import com.example.item.domain.Uom;
import com.example.item.domain.ItemDocument;
import com.example.item.dto.DocumentDto;
import com.example.item.dto.ItemDto;
import com.example.item.repository.ItemRepository;
import com.example.item.repository.UomRepository;
import com.example.item.repository.ItemDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;
    private final UomRepository uomRepository;
    private final ItemDocumentRepository itemDocumentRepository;

    public ItemServiceImpl(ItemRepository itemRepository, UomRepository uomRepository,
            ItemDocumentRepository itemDocumentRepository) {
        this.itemRepository = itemRepository;
        this.uomRepository = uomRepository;
        this.itemDocumentRepository = itemDocumentRepository;
    }

    @Override
    public ItemDto createItem(ItemDto itemDto) {
        if (itemDto.getName() == null || itemDto.getName().isEmpty()) {
            throw new RuntimeException("Item name is required");
        }
        if (itemDto.getSkuCode() == null || itemDto.getSkuCode().isEmpty()) {
            throw new RuntimeException("Item SKU code is required");
        }
        if (itemDto.getItemType() == null) {
            throw new RuntimeException("Item type is required");
        }
        Item saved = itemRepository.save(toEntity(itemDto));
        return new ItemDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ItemDto getItemById(UUID id) {
        Item item = itemRepository.findById(id).orElseThrow(() -> new RuntimeException("Item not found"));
        return new ItemDto(item);
    }

    @Override
    @Transactional(readOnly = true)
    public ItemDto getItemBySkuCode(String skuCode) {
        Item item = itemRepository.findBySkuCode(skuCode).orElseThrow(() -> new RuntimeException("Item not found"));
        return new ItemDto(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemDto> getAllItems() {
        return itemRepository.findAll().stream().map(ItemDto::new).collect(Collectors.toList());
    }

    @Override
    public ItemDto updateItem(ItemDto itemDto) {
        Item existing = itemRepository.findById(itemDto.getId())
                .orElseThrow(() -> new RuntimeException("Item not found"));
        existing.setName(itemDto.getName());
        existing.setSkuCode(itemDto.getSkuCode());
        existing.setItemType(itemDto.getItemType());
        existing.setActive(itemDto.getActive());
        existing.setDescription(itemDto.getDescription());
        if (itemDto.getBaseUomId() != null) {
            Uom uom = uomRepository.findById(itemDto.getBaseUomId())
                    .orElseThrow(() -> new RuntimeException("UOM not found"));
            existing.setBaseUom(uom);
        }
        Item saved = itemRepository.save(existing);
        return new ItemDto(saved);
    }

    @Override
    public void deleteItem(UUID id) {
        itemRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDto> getDocumentsByItemId(UUID id) {
        return itemDocumentRepository.findAllByItemId(id).stream()
                .map(ItemDocument::getDocument)
                .map(DocumentDto::new)
                .collect(Collectors.toList());
    }

    private Item toEntity(ItemDto dto) {
        Item item = new Item();
        item.setId(dto.getId());
        item.setSkuCode(dto.getSkuCode());
        item.setName(dto.getName());
        item.setItemType(dto.getItemType());
        item.setActive(dto.getActive() != null ? dto.getActive() : true);
        item.setDescription(dto.getDescription());
        if (dto.getBaseUomId() != null) {
            Uom uom = uomRepository.findById(dto.getBaseUomId())
                    .orElseThrow(() -> new RuntimeException("UOM not found"));
            item.setBaseUom(uom);
        }
        return item;
    }
}

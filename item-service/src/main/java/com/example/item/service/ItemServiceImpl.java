package com.example.item.service;

import com.example.item.domain.Item;
import com.example.item.domain.Uom;
import com.example.document.domain.DocumentLink;
import com.example.document.domain.DocumentLink.LinkType;
import com.example.item.dto.ItemDto;
import com.example.item.repository.ItemRepository;
import com.example.item.repository.UomRepository;
import com.example.document.repository.DocumentLinkRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final DocumentLinkRepository documentLinkRepository;

    public ItemServiceImpl(ItemRepository itemRepository, UomRepository uomRepository,
            DocumentLinkRepository documentLinkRepository) {
        this.itemRepository = itemRepository;
        this.uomRepository = uomRepository;
        this.documentLinkRepository = documentLinkRepository;
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
    @Transactional(readOnly = true)
    public Page<ItemDto> searchItems(String keyword, Item.ItemType itemType, Boolean active, Pageable pageable) {
        Page<Item> page = itemRepository.search(
                keyword,
                itemType != null ? itemType.name() : null,
                active,
                pageable);
        return page.map(ItemDto::new);
    }

    @Override
    public ItemDto updateItem(ItemDto itemDto) {
        if (itemDto.getId() == null) {
            throw new RuntimeException("Item ID is required");
        }
        if (itemDto.getBaseUomId() != null) {
            uomRepository.findById(itemDto.getBaseUomId())
                    .orElseThrow(() -> new RuntimeException("UOM not found"));
        }

        int updated = itemRepository.patchItem(
                itemDto.getId(),
                itemDto.getName(),
                itemDto.getSkuCode(),
                itemDto.getItemType() != null ? itemDto.getItemType().name() : null,
                itemDto.getActive(),
                itemDto.getDescription(),
                itemDto.getBaseUomId());

        if (updated == 0) {
            throw new RuntimeException("Item not found");
        }
        Item reloaded = itemRepository.findById(itemDto.getId())
                .orElseThrow(() -> new RuntimeException("Item not found"));
        return new ItemDto(reloaded);
    }

    @Override
    public void deleteItem(UUID id) {
        itemRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getDocumentsByItemId(UUID id) {
        return documentLinkRepository.findAllByLinkIdAndLinkType(id, LinkType.ITEM).stream()
                .map(DocumentLink::getDocumentId)
                .collect(Collectors.toList());
    }

    private Item toEntity(ItemDto dto) {
        Item item = new Item();
        if (dto.getId() != null) {
            item.setId(dto.getId());
        }
        if (dto.getSkuCode() != null) {
            item.setSkuCode(dto.getSkuCode());
        }
        if (dto.getName() != null) {
            item.setName(dto.getName());
        }
        if (dto.getItemType() != null) {
            item.setItemType(dto.getItemType());
        }
        if (dto.getActive() != null) {
            item.setActive(dto.getActive());
        }
        if (dto.getDescription() != null) {
            item.setDescription(dto.getDescription());
        }
        if (dto.getBaseUomId() != null) {
            Uom uom = uomRepository.findById(dto.getBaseUomId())
                    .orElseThrow(() -> new RuntimeException("UOM not found"));
            item.setBaseUom(uom);
        }
        return item;
    }
}

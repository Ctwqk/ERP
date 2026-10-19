package com.example.item.service;

import com.example.document.domain.DocumentLink;
import com.example.document.domain.DocumentLink.LinkType;
import com.example.document.repository.DocumentLinkRepository;
import com.example.item.domain.Item;
import com.example.item.domain.Uom;
import com.example.item.dto.ItemDto;
import com.example.item.repository.ItemRepository;
import com.example.item.repository.UomRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        if (isBlank(itemDto.getName())) {
            throw bad("Item name is required");
        }
        if (isBlank(itemDto.getSkuCode())) {
            throw bad("Item SKU code is required");
        }
        if (itemDto.getItemType() == null) {
            throw bad("Item type is required");
        }
        if (itemDto.getBaseUomId() == null) {
            throw bad("Base unit of measure (baseUomId) is required");
        }
        String sku = itemDto.getSkuCode().trim();
        if (itemRepository.findBySkuCode(sku).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists: " + sku);
        }
        Uom uom = uomRepository.findById(itemDto.getBaseUomId()).orElseThrow(() -> bad("UOM not found"));

        Item item = new Item();
        item.setSkuCode(sku);
        item.setName(itemDto.getName().trim());
        item.setItemType(itemDto.getItemType());
        item.setBaseUom(uom);
        item.setDescription(itemDto.getDescription());
        if (itemDto.getActive() != null) {
            item.setActive(itemDto.getActive());
        }
        return new ItemDto(itemRepository.save(item));
    }

    @Override
    @Transactional(readOnly = true)
    public ItemDto getItemById(UUID id) {
        return new ItemDto(itemRepository.findById(id).orElseThrow(ItemServiceImpl::notFound));
    }

    @Override
    @Transactional(readOnly = true)
    public ItemDto getItemBySkuCode(String skuCode) {
        return new ItemDto(itemRepository.findBySkuCode(skuCode).orElseThrow(ItemServiceImpl::notFound));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemDto> getAllItems() {
        return itemRepository.findAll().stream().map(ItemDto::new).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ItemDto> searchItems(String keyword, Item.ItemType itemType, Boolean active, Pageable pageable) {
        return itemRepository.search(
                isBlank(keyword) ? null : keyword.trim(),
                itemType,
                active,
                pageable).map(ItemDto::new);
    }

    @Override
    public ItemDto updateItem(ItemDto itemDto) {
        if (itemDto.getId() == null) {
            throw bad("Item ID is required");
        }
        if (itemDto.getBaseUomId() != null && !uomRepository.existsById(itemDto.getBaseUomId())) {
            throw bad("UOM not found");
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
            throw notFound();
        }
        return new ItemDto(itemRepository.findById(itemDto.getId()).orElseThrow(ItemServiceImpl::notFound));
    }

    @Override
    public void deleteItem(UUID id) {
        if (!itemRepository.existsById(id)) {
            throw notFound();
        }
        itemRepository.deleteById(id);
        itemRepository.flush(); // surface FK violations (item still used by a BOM) as 409 right here
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getDocumentsByItemId(UUID id) {
        if (!itemRepository.existsById(id)) {
            throw notFound();
        }
        return documentLinkRepository.findAllByLinkIdAndLinkType(id, LinkType.ITEM).stream()
                .map(DocumentLink::getDocumentId)
                .collect(Collectors.toList());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
    }
}

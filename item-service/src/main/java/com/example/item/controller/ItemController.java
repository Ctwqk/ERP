package com.example.item.controller;

import com.example.item.dto.ItemDto;
import com.example.item.domain.Item;
import com.example.item.service.ItemService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/items")
@Tag(name = "Items", description = "Item master data (SKUs)")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an item", description = "name, skuCode, itemType and baseUomId are required; the SKU must be unique (409 otherwise)")
    public ItemDto create(@RequestBody ItemDto dto) {
        return itemService.createItem(dto);
    }

    @GetMapping("/{id}")
    public ItemDto getById(@PathVariable UUID id) {
        return itemService.getItemById(id);
    }

    @GetMapping("/sku/{skuCode}")
    public ItemDto getBySku(@PathVariable String skuCode) {
        return itemService.getItemBySkuCode(skuCode);
    }

    @GetMapping
    public Page<ItemDto> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Item.ItemType itemType,
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return itemService.searchItems(keyword, itemType, active, pageable);
    }

    @PutMapping("/{id}")
    public ItemDto update(@PathVariable UUID id, @RequestBody ItemDto dto) {
        dto.setId(id); // the id in the path wins
        return itemService.updateItem(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        itemService.deleteItem(id);
    }

    @GetMapping("/{id}/documents")
    public List<UUID> listDocuments(@PathVariable UUID id) {
        return itemService.getDocumentsByItemId(id);
    }

}
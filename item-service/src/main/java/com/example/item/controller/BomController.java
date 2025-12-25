package com.example.item.controller;

import com.example.item.dto.BomDto;
import com.example.item.dto.BomLineDto;
import com.example.item.domain.Bom;
import com.example.item.service.BomService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/boms")
public class BomController {

    private final BomService bomService;

    public BomController(BomService bomService) {
        this.bomService = bomService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BomDto create(@RequestBody BomDto dto) {
        return bomService.createBom(dto);
    }

    @GetMapping("/{id}")
    public BomDto getById(@PathVariable UUID id) {
        return bomService.getBomById(id);
    }

    @GetMapping
    public Page<BomDto> list(@RequestParam(required = false) UUID productItemId,
            @RequestParam(required = false) String revision,
            @RequestParam(required = false) Bom.BomStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return bomService.searchBoms(productItemId, revision, status, pageable);
    }

    @PutMapping("/{id}")
    public BomDto update(@PathVariable UUID id, @RequestBody BomDto dto) {
        dto.setId(id); // 确保使用路径中的 id
        return bomService.updateBom(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        bomService.deleteBom(id);
    }

    @GetMapping("/{id}/lines")
    public List<BomLineDto> listLines(@PathVariable UUID id) {
        return bomService.getBomLinesByBomId(id);
    }
}
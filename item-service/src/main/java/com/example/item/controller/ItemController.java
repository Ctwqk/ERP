package com.example.item.controller;

import com.example.item.dto.BomDto;
import com.example.item.dto.BomLineDto;
import com.example.item.service.BomService;
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
    public List<BomDto> list() {
        return bomService.getAllBoms();
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
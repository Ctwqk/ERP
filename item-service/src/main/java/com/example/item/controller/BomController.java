package com.example.item.controller;

import com.example.item.dto.BomDto;
import com.example.item.dto.BomLineDto;
import com.example.item.dto.BomRequirementDto;
import com.example.item.domain.Bom;
import com.example.item.service.BomService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/boms")
@Tag(name = "BOMs", description = "Bills of materials: DRAFT -> ACTIVE -> OBSOLETE, one ACTIVE per product revision")
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
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return bomService.searchBoms(productItemId, revision, status, pageable);
    }

    @PutMapping("/{id}")
    public BomDto update(@PathVariable UUID id, @RequestBody BomDto dto) {
        dto.setId(id); // the id in the path wins
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

    @PostMapping("/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a component line to a DRAFT BOM",
            description = "lineNo defaults to the next multiple of 10, uomId to the component's base UOM, scrapRate to 0")
    public BomLineDto addLine(@PathVariable UUID id, @RequestBody BomLineDto line) {
        return bomService.addLine(id, line);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        bomService.removeLine(id, lineId);
    }

    @GetMapping("/{id}/requirements")
    @Operation(summary = "Material requirements for building `quantity` units (single level)",
            description = "net = qtyPer * quantity; gross = net / (1 - scrapRate)")
    public List<BomRequirementDto> requirements(@PathVariable UUID id,
            @RequestParam(defaultValue = "1") BigDecimal quantity) {
        return bomService.requirements(id, quantity);
    }
}

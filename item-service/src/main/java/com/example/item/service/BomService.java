package com.example.item.service;

import com.example.item.domain.Bom;
import com.example.item.dto.BomDto;
import com.example.item.dto.BomLineDto;
import com.example.item.dto.BomRequirementDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BomService {
    BomDto createBom(BomDto bomDto);

    BomDto getBomById(UUID id);

    List<BomDto> getAllBoms();

    Page<BomDto> searchBoms(UUID productItemId, String revision, Bom.BomStatus status, Pageable pageable);

    BomDto updateBom(BomDto bomDto);

    void deleteBom(UUID id);

    List<BomLineDto> getBomLinesByBomId(UUID id);

    /** Adds a component line to a DRAFT BOM; the line number defaults to the next free one. */
    BomLineDto addLine(UUID bomId, BomLineDto line);

    /** Removes a line from a DRAFT BOM. */
    void removeLine(UUID bomId, UUID lineId);

    /** Single-level explosion: component quantities needed to build {@code quantity} units of the product. */
    List<BomRequirementDto> requirements(UUID bomId, BigDecimal quantity);
}

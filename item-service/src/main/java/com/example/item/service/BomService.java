package com.example.item.service;

import com.example.item.dto.BomDto;
import com.example.item.dto.BomLineDto;
import com.example.item.domain.Bom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface BomService {
    BomDto createBom(BomDto bomDto);

    BomDto getBomById(UUID id);

    List<BomDto> getAllBoms();

    Page<BomDto> searchBoms(UUID productItemId, String revision, Bom.BomStatus status, Pageable pageable);

    BomDto updateBom(BomDto bomDto);

    void deleteBom(UUID id);

    List<BomLineDto> getBomLinesByBomId(UUID id);
}

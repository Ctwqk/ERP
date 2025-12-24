package com.example.item.service;

import com.example.item.dto.BomDto;
import com.example.item.dto.BomLineDto;
import java.util.List;
import java.util.UUID;

public interface BomService {
    BomDto createBom(BomDto bomDto);

    BomDto getBomById(UUID id);

    List<BomDto> getAllBoms();

    BomDto updateBom(BomDto bomDto);

    void deleteBom(UUID id);

    List<BomLineDto> getBomLinesByBomId(UUID id);
}

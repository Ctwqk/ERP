package com.example.item.service;

import com.example.item.domain.Bom;
import com.example.item.domain.BomLine;
import com.example.item.domain.Item;
import com.example.item.dto.BomDto;
import com.example.item.dto.BomLineDto;
import com.example.item.repository.BomLineRepository;
import com.example.item.repository.BomRepository;
import com.example.item.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class BomServiceImpl implements BomService {

    private final BomRepository bomRepository;
    private final BomLineRepository bomLineRepository;
    private final ItemRepository itemRepository;

    public BomServiceImpl(BomRepository bomRepository, BomLineRepository bomLineRepository,
            ItemRepository itemRepository) {
        this.bomRepository = bomRepository;
        this.bomLineRepository = bomLineRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    public BomDto createBom(BomDto bomDto) {
        if (bomDto.getProductItemId() == null) {
            throw new RuntimeException("Product item ID is required");
        }
        if (bomDto.getRevision() == null || bomDto.getRevision().isEmpty()) {
            throw new RuntimeException("Revision is required");
        }
        if (bomDto.getStatus() == null) {
            throw new RuntimeException("Status is required");
        }
        Bom saved = bomRepository.save(toEntity(bomDto));
        return new BomDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BomDto getBomById(UUID id) {
        Bom bom = bomRepository.findById(id).orElseThrow(() -> new RuntimeException("BOM not found"));
        return new BomDto(bom);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BomDto> getAllBoms() {
        return bomRepository.findAll().stream().map(BomDto::new).collect(Collectors.toList());
    }

    @Override
    public BomDto updateBom(BomDto bomDto) {
        Bom existing = bomRepository.findById(bomDto.getId())
                .orElseThrow(() -> new RuntimeException("BOM not found"));
        existing.setRevision(bomDto.getRevision());
        existing.setStatus(bomDto.getStatus());
        existing.setEffectiveFrom(bomDto.getEffectiveFrom());
        existing.setEffectiveTo(bomDto.getEffectiveTo());
        existing.setNote(bomDto.getNote());
        // 不直接更新 lines，这里保持简单，如需同步行可扩展
        Bom saved = bomRepository.save(existing);
        return new BomDto(saved);
    }

    @Override
    public void deleteBom(UUID id) {
        bomRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BomLineDto> getBomLinesByBomId(UUID id) {
        Bom bom = bomRepository.findById(id).orElseThrow(() -> new RuntimeException("BOM not found"));
        return bomLineRepository.findAllByBom(bom).stream()
                .map(BomLineDto::new)
                .collect(Collectors.toList());
    }

    private Bom toEntity(BomDto dto) {
        Bom bom = new Bom();
        if (dto.getId() != null) {
            bom.setId(dto.getId());
        }
        if (dto.getProductItemId() != null) {
            Item item = itemRepository.findById(dto.getProductItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found"));
            bom.setProductItem(item);
        }
        bom.setRevision(dto.getRevision());
        bom.setStatus(dto.getStatus());
        bom.setEffectiveFrom(dto.getEffectiveFrom());
        bom.setEffectiveTo(dto.getEffectiveTo());
        bom.setNote(dto.getNote());
        // 不处理行，保持简单；行通常单独接口维护
        return bom;
    }
}

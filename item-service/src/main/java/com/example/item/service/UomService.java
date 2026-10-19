package com.example.item.service;

import com.example.item.domain.Uom;
import com.example.item.dto.UomDto;
import com.example.item.repository.UomRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class UomService {

    private final UomRepository uomRepository;

    public UomService(UomRepository uomRepository) {
        this.uomRepository = uomRepository;
    }

    public UomDto create(String code, String name) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code and name are required");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (uomRepository.findByCode(normalized).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "UOM code already exists: " + normalized);
        }
        Uom uom = new Uom();
        uom.setCode(normalized);
        uom.setName(name.trim());
        return new UomDto(uomRepository.save(uom));
    }

    @Transactional(readOnly = true)
    public List<UomDto> list() {
        return uomRepository.findAll().stream().map(UomDto::new).toList();
    }

    @Transactional(readOnly = true)
    public UomDto get(UUID id) {
        return new UomDto(uomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UOM not found")));
    }
}

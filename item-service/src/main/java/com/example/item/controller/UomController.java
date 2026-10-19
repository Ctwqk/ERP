package com.example.item.controller;

import com.example.item.dto.UomDto;
import com.example.item.service.UomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uoms")
@Tag(name = "Units of measure", description = "EA, KG, G, M, L and BOX are seeded by the database migration")
public class UomController {

    public record CreateUomRequest(@NotBlank String code, @NotBlank String name) {
    }

    private final UomService uomService;

    public UomController(UomService uomService) {
        this.uomService = uomService;
    }

    @GetMapping
    public List<UomDto> list() {
        return uomService.list();
    }

    @GetMapping("/{id}")
    public UomDto get(@PathVariable UUID id) {
        return uomService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a unit of measure; the code is upper-cased and must be unique")
    public UomDto create(@Valid @RequestBody CreateUomRequest request) {
        return uomService.create(request.code(), request.name());
    }
}

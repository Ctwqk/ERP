package com.example.item.repository;

import com.example.item.domain.BomLine;
import com.example.item.domain.Bom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BomLineRepository extends JpaRepository<BomLine, UUID> {
    List<BomLine> findAllByBom(Bom bom);
}




package com.example.item.repository;

import com.example.item.domain.Bom;
import com.example.item.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BomRepository extends JpaRepository<Bom, UUID> {
    List<Bom> findAllByProductItem(Item productItem);
}




package com.example.item.repository;

import com.example.item.domain.Uom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UomRepository extends JpaRepository<Uom, UUID> {
    Optional<Uom> findByCode(String code);
}




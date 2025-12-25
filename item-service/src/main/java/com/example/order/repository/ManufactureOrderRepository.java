package com.example.order.repository;

import com.example.order.domain.ManufactureOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManufactureOrderRepository extends JpaRepository<ManufactureOrder, UUID> {
    Optional<ManufactureOrder> findByMoCode(String moCode);
}


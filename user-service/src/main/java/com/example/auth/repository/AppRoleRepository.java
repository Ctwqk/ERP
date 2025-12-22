package com.example.auth.repository;

import com.example.auth.domain.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppRoleRepository extends JpaRepository<AppRole, UUID> {
    Optional<AppRole> findByName(String name);

    List<AppRole> findAll();

    List<AppRole> findAllByIdIn(List<UUID> ids);

    List<AppRole> findAllByNameIn(List<String> names);

    List<AppRole> findAllByNameNotIn(List<String> names);

    List<AppRole> findAllByNameAndNameNot(String name, String name2);
}

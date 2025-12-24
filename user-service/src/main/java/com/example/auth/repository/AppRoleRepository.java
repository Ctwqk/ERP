package com.example.auth.repository;

import com.example.auth.domain.AppRole;
import com.example.auth.domain.AppRole.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppRoleRepository extends JpaRepository<AppRole, UUID> {
    Optional<AppRole> findByName(RoleName name);

    List<AppRole> findAll();

    List<AppRole> findAllByIdIn(List<UUID> ids);

    List<AppRole> findAllByNameIn(List<RoleName> names);

    List<AppRole> findAllByNameNotIn(List<RoleName> names);

    List<AppRole> findAllByNameAndNameNot(RoleName name, RoleName name2);
}

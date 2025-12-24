package com.example.auth.repository;

import com.example.auth.domain.AppUserRole;
import com.example.auth.domain.AppRole.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRoleRepository extends JpaRepository<AppUserRole, AppUserRole.AppUserRoleId> {
    Optional<AppUserRole> findByUserIdAndRoleId(UUID userId, UUID roleId);

    List<AppUserRole> findAllByUserId(UUID userId);

    List<AppUserRole> findAllByRoleName(RoleName roleName);

    List<AppUserRole> findAllByRoleId(UUID roleId);

    List<AppUserRole> findAllByUserIdIn(List<UUID> userIds);

    List<AppUserRole> findAllByRoleIdIn(List<UUID> roleIds);

    List<AppUserRole> findAllByUserIdAndRoleId(UUID userId, UUID roleId);

    void deleteByUserIdAndRoleId(UUID userId, UUID roleId);

    boolean existsByUserIdAndRoleName(UUID userId, RoleName roleName);

    Optional<AppUserRole> findByUserIdAndRoleName(UUID userId, RoleName roleName);
}

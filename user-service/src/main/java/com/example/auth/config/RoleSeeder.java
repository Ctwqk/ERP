package com.example.auth.config;

import com.example.auth.domain.AppRole;
import com.example.auth.domain.AppRole.RoleName;
import com.example.auth.repository.AppRoleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seed default roles at startup. Idempotent: only inserts missing ones.
 */
@Component
public class RoleSeeder implements ApplicationRunner {

    private final AppRoleRepository roleRepository;

    public RoleSeeder(AppRoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<RoleName> existing = roleRepository.findAll().stream()
                .map(AppRole::getName)
                .collect(Collectors.toSet());

        for (RoleName roleName : EnumSet.allOf(RoleName.class)) {
            if (!existing.contains(roleName)) {
                roleRepository.save(new AppRole(roleName));
            }
        }
    }
}

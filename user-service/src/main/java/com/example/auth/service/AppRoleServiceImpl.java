package com.example.auth.service;

import com.example.auth.domain.AppRole;
import com.example.auth.repository.AppRoleRepository;
import java.util.UUID;
import java.util.List;

public class AppRoleServiceImpl implements AppRoleService {
    private final AppRoleRepository appRoleRepository;

    public AppRoleServiceImpl(AppRoleRepository appRoleRepository) {
        this.appRoleRepository = appRoleRepository;
    }

    @Override
    public AppRole createRole(String roleName) {
        return appRoleRepository.save(new AppRole(roleName));
    }

    @Override
    public AppRole getRoleByName(String name) {
        return appRoleRepository.findByName(name).orElseThrow(() -> new RuntimeException("Role not found"));
    }

    @Override
    public AppRole updateRole(AppRole role) {
        return appRoleRepository.save(role);
    }

    @Override
    public void deleteRole(UUID id) {
        appRoleRepository.deleteById(id);
    }

    @Override
    public List<AppRole> getAllRoles() {
        return appRoleRepository.findAll();
    }

    @Override
    public AppRole getRoleById(UUID id) {
        return appRoleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
    }
}

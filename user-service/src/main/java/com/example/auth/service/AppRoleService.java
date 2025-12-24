package com.example.auth.service;

import com.example.auth.dto.AppRoleDto;

import java.util.List;
import java.util.UUID;

public interface AppRoleService {
    AppRoleDto createRole(String roleName);

    AppRoleDto getRoleById(UUID id);

    AppRoleDto getRoleByName(String name);

    List<AppRoleDto> getAllRoles();

    List<AppRoleDto> getRolesByUserIds(List<UUID> ids);

    List<AppRoleDto> getRolesByUserEmails(List<String> emails);

    AppRoleDto updateRole(AppRoleDto role);

    void deleteRole(UUID id);
}

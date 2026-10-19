package com.example.auth.service;

import com.example.auth.dto.AppRoleDto;
import java.util.List;
import java.util.UUID;

public interface AppRoleService {
    AppRoleDto getRoleById(UUID id);

    AppRoleDto getRoleByName(String name);

    List<AppRoleDto> getAllRoles();

    void deleteRole(UUID id);
}

package com.example.auth.service;

import com.example.auth.domain.AppRole;
import com.example.auth.domain.AppRole.RoleName;
import com.example.auth.domain.AppUserRole;
import java.util.stream.Collectors;
import com.example.auth.repository.AppRoleRepository;
import com.example.auth.repository.AppUserRoleRepository;
import com.example.auth.repository.AppUserRepository;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.auth.dto.AppRoleDto;
import com.example.auth.dto.AppUserDto;

@Service
@Transactional
public class AppRoleServiceImpl implements AppRoleService {
    private final AppRoleRepository appRoleRepository;
    private final AppUserRoleRepository appUserRoleRepository;
    private final AppUserRepository appUserRepository;

    public AppRoleServiceImpl(AppRoleRepository appRoleRepository, AppUserRoleRepository appUserRoleRepository,
            AppUserRepository appUserRepository) {
        this.appRoleRepository = appRoleRepository;
        this.appUserRoleRepository = appUserRoleRepository;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public AppRoleDto createRole(String roleName) {
        RoleName rn = parse(roleName);
        AppRole role = appRoleRepository.save(new AppRole(rn));
        return new AppRoleDto(role.getId(), role.getName(), role.getCreatedAt(), role.getUpdatedAt(), null);
    }

    @Override
    public AppRoleDto getRoleByName(String name) {
        RoleName roleName = parse(name);
        AppRole role = appRoleRepository.findByName(roleName).orElseThrow(() -> new RuntimeException("Role not found"));
        return new AppRoleDto(role);
    }

    @Override
    public List<AppRoleDto> getRolesByUserIds(List<UUID> ids) {

        List<AppRoleDto> roles = new ArrayList<>();
        for (UUID id : ids) {
            roles.add(new AppRoleDto(
                    appRoleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"))));
            roles.get(roles.size() - 1).setUsers(appUserRoleRepository.findAllByUserId(id).stream()
                    .map(AppUserRole::getUser).map(AppUserDto::new).collect(Collectors.toList()));
        }
        return roles;
    }

    @Override
    public List<AppRoleDto> getRolesByUserEmails(List<String> emails) {
        List<AppRoleDto> roles = new ArrayList<>();
        for (String email : emails) {
            roles.add(new AppRoleDto(appRoleRepository.findById(appUserRepository.findByEmail(email).get().getId())
                    .orElseThrow(() -> new RuntimeException("Role not found"))));
            roles.get(roles.size() - 1)
                    .setUsers(appUserRoleRepository.findAllByUserId(appUserRepository.findByEmail(email).get().getId())
                            .stream().map(AppUserRole::getUser).map(AppUserDto::new).collect(Collectors.toList()));
        }
        return roles;
    }

    @Override
    public AppRoleDto updateRole(AppRoleDto role) {
        RoleName rn = parse(role.getName());
        return new AppRoleDto(appRoleRepository.save(new AppRole(rn)));
    }

    @Override
    public void deleteRole(UUID id) {
        appRoleRepository.deleteById(id);
    }

    @Override
    public List<AppRoleDto> getAllRoles() {
        return appRoleRepository.findAll().stream().map(AppRoleDto::new).collect(Collectors.toList());
    }

    @Override
    public AppRoleDto getRoleById(UUID id) {
        return new AppRoleDto(appRoleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found")));
    }

    private RoleName parse(String name) {
        try {
            return RoleName.valueOf(name.toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("Invalid role name");
        }
    }
}

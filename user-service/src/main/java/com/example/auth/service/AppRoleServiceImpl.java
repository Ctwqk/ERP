package com.example.auth.service;

import com.example.auth.domain.AppRole.RoleName;
import com.example.auth.dto.AppRoleDto;
import com.example.auth.repository.AppRoleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AppRoleServiceImpl implements AppRoleService {

    private final AppRoleRepository appRoleRepository;

    public AppRoleServiceImpl(AppRoleRepository appRoleRepository) {
        this.appRoleRepository = appRoleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AppRoleDto getRoleByName(String name) {
        return new AppRoleDto(appRoleRepository.findByName(parse(name)).orElseThrow(AppRoleServiceImpl::notFound));
    }

    @Override
    @Transactional(readOnly = true)
    public AppRoleDto getRoleById(UUID id) {
        return new AppRoleDto(appRoleRepository.findById(id).orElseThrow(AppRoleServiceImpl::notFound));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppRoleDto> getAllRoles() {
        return appRoleRepository.findAll().stream().map(AppRoleDto::new).toList();
    }

    @Override
    public void deleteRole(UUID id) {
        if (!appRoleRepository.existsById(id)) {
            throw notFound();
        }
        appRoleRepository.deleteById(id);
    }

    private static RoleName parse(String name) {
        try {
            return RoleName.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role name: " + name);
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
    }
}

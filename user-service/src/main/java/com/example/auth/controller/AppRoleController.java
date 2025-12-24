package com.example.auth.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.auth.service.AppRoleService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.example.auth.domain.AppRole.RoleName;
import com.example.auth.dto.AppRoleDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
public class AppRoleController {
    private final AppRoleService appRoleService;

    public AppRoleController(AppRoleService appRoleService) {
        this.appRoleService = appRoleService;
    }

    // @PostMapping
    // public AppRoleDto create(@RequestBody String name) {
    // return appRoleService.createRole(name);
    // }

    @GetMapping
    public List<AppRoleDto> list() {
        return appRoleService.getAllRoles();
    }

    @GetMapping("/{id}")
    public AppRoleDto getById(@PathVariable UUID id) {
        return appRoleService.getRoleById(id);
    }

    @GetMapping("/by-name/{name}")
    // Allowed role names: ADMIN, HR, SALE, FINANCE, MANAGER, EMPLOYEE, USER, GUEST
    public AppRoleDto getByName(@PathVariable String name) {
        return appRoleService.getRoleByName(name);
    }

    // @PostMapping("/update")
    // public AppRoleDto update(@RequestBody AppRoleDto dto) {
    // return appRoleService.updateRole(dto);
    // }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        appRoleService.deleteRole(id);
    }

}

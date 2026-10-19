package com.example.auth.controller;

import com.example.auth.dto.AppRoleDto;
import com.example.auth.service.AppRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles")
public class AppRoleController {
    private final AppRoleService appRoleService;

    public AppRoleController(AppRoleService appRoleService) {
        this.appRoleService = appRoleService;
    }

    @GetMapping
    public List<AppRoleDto> list() {
        return appRoleService.getAllRoles();
    }

    @GetMapping("/{id}")
    public AppRoleDto getById(@PathVariable UUID id) {
        return appRoleService.getRoleById(id);
    }

    @GetMapping("/by-name/{name}")
    @Operation(summary = "Find a role by name",
            description = "Allowed names: ADMIN, HR, SALE, FINANCE, MANAGER, EMPLOYEE, USER, GUEST")
    public AppRoleDto getByName(@PathVariable String name) {
        return appRoleService.getRoleByName(name);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        appRoleService.deleteRole(id);
    }
}

package com.example.auth.controller;

import com.example.auth.dto.AppUserDto;
import com.example.auth.domain.AppRole.RoleName;
import com.example.auth.repository.AppUserRoleRepository;
import com.example.auth.service.AppUserService;
import com.example.auth.service.AppRoleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import com.example.auth.security.SecurityUtils;

@RestController
@RequestMapping("/api/users")
public class AppUserController {

    private final AppUserService userService;
    private final AppRoleService roleService;
    private final AppUserRoleRepository appUserRoleRepository;

    public AppUserController(AppUserService userService, AppRoleService roleService,
            AppUserRoleRepository appUserRoleRepository) {
        this.userService = userService;
        this.roleService = roleService;
        this.appUserRoleRepository = appUserRoleRepository;
    }

    @GetMapping
    public Page<AppUserDto> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20) Pageable pageable) {
        return userService.searchUsers(keyword, active, pageable);
    }

    @GetMapping("/{id}")
    public AppUserDto getById(@PathVariable UUID id) {
        return userService.getUserById(id);
    }

    @GetMapping("/by-email")
    public AppUserDto getByEmail(@RequestParam String email) {
        return userService.getUserByEmail(email);
    }

    @PutMapping
    public AppUserDto update(@RequestBody AppUserDto dto) {
        return userService.updateUser(dto);
    }

    @PostMapping("/assign-role")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public void assignRole(@RequestParam UUID userId, @RequestParam String roleName) {
        if (!SecurityUtils.currentUserRolesOrThrow().contains("ADMIN")
                && SecurityUtils.currentUserRolesOrThrow().contains("HR")
                && !roleService.getRoleByName(roleName).getName().equals("HR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not authorized to assign OTHER role to this user");
        }
        userService.assignRoleToUser(userId, roleService.getRoleByName(roleName).getId());
    }

    @PostMapping("/remove-role")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public void removeRole(@RequestParam UUID userId, @RequestParam UUID roleId) {
        if (SecurityUtils.currentUserRolesOrThrow().contains("HR")
                && appUserRoleRepository.existsByUserIdAndRoleName(userId, RoleName.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not authorized to remove ADMIN role from this user");
        }
        userService.removeRoleFromUser(userId, roleId);
    }

    @PostMapping("/change-password")
    public void changePassword(@RequestParam String oldPassword, @RequestParam String newPassword) {
        UUID userId = SecurityUtils.currentUserIdOrThrow();
        userService.changePassword(userId, oldPassword, newPassword);
    }

    @PostMapping("/createUser")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public AppUserDto createUser(@RequestParam String userName, @RequestParam String email,
            @RequestParam String password) {
        return userService.createUser(userName, email, password);
    }

    @PostMapping("/disableUser")
    @PreAuthorize("hasRole('ADMIN', 'HR')")
    public void disableUser(@RequestParam UUID userId) {
        if (SecurityUtils.currentUserRolesOrThrow().contains("HR")
                && userService.getUserById(userId).getRoles().contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to disable ADMIN user");
        }
        userService.disableUser(userId);
    }

    @PostMapping("/enableUser")
    @PreAuthorize("hasRole('ADMIN', 'HR')")
    public void enableUser(@RequestParam UUID userId) {
        userService.enableUser(userId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        userService.deleteUser(id);
    }
}

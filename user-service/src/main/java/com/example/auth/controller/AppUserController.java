package com.example.auth.controller;

import com.example.auth.domain.AppRole.RoleName;
import com.example.auth.dto.AppUserDto;
import com.example.auth.dto.ChangePasswordRequest;
import com.example.auth.dto.CreateUserRequest;
import com.example.auth.security.SecurityUtils;
import com.example.auth.service.AppRoleService;
import com.example.auth.service.AppUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Account management. ADMIN and HR manage accounts; HR cannot touch ADMINs.")
public class AppUserController {

    private final AppUserService userService;
    private final AppRoleService roleService;

    public AppUserController(AppUserService userService, AppRoleService roleService) {
        this.userService = userService;
        this.roleService = roleService;
    }

    @GetMapping("/me")
    @Operation(summary = "The caller's own account")
    public AppUserDto me() {
        return userService.getUserById(SecurityUtils.currentUserIdOrThrow());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public Page<AppUserDto> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return userService.searchUsers(keyword, active, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Look up a user by id", description = "Any authenticated caller; other services use it to "
            + "verify that a user exists")
    public AppUserDto getById(@PathVariable UUID id) {
        return userService.getUserById(id);
    }

    @GetMapping("/by-email")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public AppUserDto getByEmail(@RequestParam String email) {
        return userService.getUserByEmail(email);
    }

    @PutMapping
    @Operation(summary = "Update a user's name/e-mail (and active flag for ADMIN/HR)",
            description = "Callers may edit themselves; ADMIN and HR may edit anyone, but HR cannot edit ADMINs.")
    public AppUserDto update(@RequestBody AppUserDto dto) {
        if (dto.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User id is required");
        }
        boolean self = dto.getId().equals(SecurityUtils.currentUserIdOrThrow());
        boolean manager = SecurityUtils.hasAnyRole("ADMIN", "HR");
        if (!self && !manager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own account");
        }
        if (!self) {
            assertHrMayManage(dto.getId());
        }
        return userService.updateUser(dto, manager);
    }

    @PostMapping("/createUser")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Create an account with the default USER role")
    public AppUserDto createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request.name(), request.email(), request.password());
    }

    @PostMapping("/assign-role")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Grant a role", description = "HR cannot grant ADMIN.")
    public void assignRole(@RequestParam UUID userId, @RequestParam String roleName) {
        var role = roleService.getRoleByName(roleName);
        if (!SecurityUtils.hasAnyRole("ADMIN") && RoleName.ADMIN.name().equals(role.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can grant the ADMIN role");
        }
        assertHrMayManage(userId);
        userService.assignRoleToUser(userId, role.getId());
    }

    @PostMapping("/remove-role")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public void removeRole(@RequestParam UUID userId, @RequestParam UUID roleId) {
        assertHrMayManage(userId);
        userService.removeRoleFromUser(userId, roleId);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(SecurityUtils.currentUserIdOrThrow(), request.oldPassword(),
                request.newPassword());
    }

    @PostMapping("/disableUser")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public void disableUser(@RequestParam UUID userId) {
        assertHrMayManage(userId);
        userService.disableUser(userId);
    }

    @PostMapping("/enableUser")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public void enableUser(@RequestParam UUID userId) {
        assertHrMayManage(userId);
        userService.enableUser(userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        userService.deleteUser(id);
    }

    /** HR may manage everyone except administrators; ADMIN may manage anyone. */
    private void assertHrMayManage(UUID targetUserId) {
        if (SecurityUtils.hasAnyRole("ADMIN")) {
            return;
        }
        List<String> targetRoles = userService.getUserById(targetUserId).getRoles();
        if (targetRoles.contains(RoleName.ADMIN.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN can manage an ADMIN account");
        }
    }
}

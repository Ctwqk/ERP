package com.example.auth.service;

import com.example.auth.domain.AppRole.RoleName;
import com.example.auth.dto.AppUserDto;
import com.example.auth.dto.AppRoleDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AppUserService {
    AppUserDto createUser(String userName, String email, String password);

    AppUserDto getUserById(UUID id);

    AppUserDto getUserByEmail(String email);

    void disableUser(UUID id);

    void enableUser(UUID id);

    List<AppUserDto> getAllUsers();

    Page<AppUserDto> searchUsers(String keyword, Boolean active, Pageable pageable);

    List<AppRoleDto> getUsersByRoleIds(List<UUID> roleIds);

    List<AppRoleDto> getUsersByRoleNames(List<RoleName> roleNames);

    void assignRoleToUser(UUID userId, UUID roleId);

    void removeRoleFromUser(UUID userId, UUID roleId);

    // void disableRoleForUser(UUID userId, UUID roleId);

    // void enableRoleForUser(UUID userId, UUID roleId);

    void changePassword(UUID userId, String oldPassword, String newPassword);

    AppUserDto updateUser(AppUserDto user);

    void deleteUser(UUID id);
}

package com.example.auth.service;

import com.example.auth.dto.AppUserDto;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AppUserService {
    AppUserDto createUser(String userName, String email, String password);

    AppUserDto getUserById(UUID id);

    AppUserDto getUserByEmail(String email);

    Page<AppUserDto> searchUsers(String keyword, Boolean active, Pageable pageable);

    void disableUser(UUID id);

    void enableUser(UUID id);

    void assignRoleToUser(UUID userId, UUID roleId);

    void removeRoleFromUser(UUID userId, UUID roleId);

    void changePassword(UUID userId, String oldPassword, String newPassword);

    /** Patches name, e-mail and (when {@code allowActiveChange}) the active flag; null fields are left alone. */
    AppUserDto updateUser(AppUserDto user, boolean allowActiveChange);

    void deleteUser(UUID id);
}

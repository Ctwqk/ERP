package com.example.auth.service;

import com.example.auth.domain.AppUser;
import com.example.auth.domain.AppUserRole;
import com.example.auth.domain.AppRole.RoleName;
import com.example.auth.repository.AppUserRepository;
import com.example.auth.repository.AppUserRoleRepository;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.auth.domain.AppRole;
import com.example.auth.repository.AppRoleRepository;
import com.example.auth.dto.AppUserDto;
import com.example.auth.dto.AppRoleDto;

@Service
@Transactional
public class AppUserServiceImpl implements AppUserService {
    private final AppUserRepository appUserRepository;
    private final AppUserRoleRepository appUserRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppRoleRepository appRoleRepository;

    public AppUserServiceImpl(AppUserRepository appUserRepository, AppUserRoleRepository appUserRoleRepository,
            PasswordEncoder passwordEncoder, AppRoleRepository appRoleRepository) {
        this.appUserRepository = appUserRepository;
        this.appUserRoleRepository = appUserRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.appRoleRepository = appRoleRepository;
    }

    @Override
    public AppUserDto createUser(String userName, String email, String password) {
        if (appUserRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("User already exists");
        }
        AppUser user = new AppUser(userName, email, passwordEncoder.encode(password));
        AppRole defaultRole = appRoleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new RuntimeException("Default role not found"));
        user.addUserRole(new AppUserRole(user, defaultRole));
        return new AppUserDto(appUserRepository.save(user));
    }

    @Override
    public AppUserDto getUserById(UUID id) {
        return new AppUserDto(appUserRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found")));
    }

    @Override
    public AppUserDto getUserByEmail(String email) {
        return new AppUserDto(
                appUserRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found")));
    }

    @Override
    public void disableUser(UUID id) {
        AppUser user = appUserRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        appUserRepository.save(user);
    }

    @Override
    public void enableUser(UUID id) {
        AppUser user = appUserRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(true);
        appUserRepository.save(user);
    }

    @Override
    public List<AppRoleDto> getUsersByRoleIds(List<UUID> roleIds) {
        List<AppRoleDto> roles = new ArrayList<>();
        for (UUID roleId : roleIds) {
            roles.add(new AppRoleDto(
                    appRoleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"))));
            roles.get(roles.size() - 1).setUsers(appUserRoleRepository.findAllByRoleId(roleId).stream()
                    .map(AppUserRole::getUser).map(AppUserDto::new).collect(Collectors.toList()));
        }
        return roles;
    }

    @Override
    public List<AppRoleDto> getUsersByRoleNames(List<RoleName> roleNames) {
        List<AppRoleDto> roles = new ArrayList<>();
        for (RoleName roleName : roleNames) {
            roles.add(new AppRoleDto(
                    appRoleRepository.findByName(roleName).orElseThrow(() -> new RuntimeException("Role not found"))));
            roles.get(roles.size() - 1).setUsers(appUserRoleRepository.findAllByRoleName(roleName).stream()
                    .map(AppUserRole::getUser).map(AppUserDto::new).collect(Collectors.toList()));
        }
        return roles;
    }

    @Override
    public void assignRoleToUser(UUID userId, UUID roleId) {
        getUserById(userId);
        appRoleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));
        AppUserRole userRole = new AppUserRole(userId, roleId);
        appUserRoleRepository.save(userRole);
    }

    @Override
    public void removeRoleFromUser(UUID userId, UUID roleId) {
        getUserById(userId);
        appRoleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));
        appUserRoleRepository.deleteByUserIdAndRoleId(userId, roleId);
    }

    @Override
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new RuntimeException("Invalid old password");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        appUserRepository.save(user);
    }

    @Override
    public AppUserDto updateUser(AppUserDto user) {
        AppUser existing = appUserRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        existing.setName(user.getName());
        existing.setEmail(user.getEmail());
        if (user.getActive() != null) {
            existing.setActive(user.getActive());
        }

        AppUser saved = appUserRepository.save(existing);
        return new AppUserDto(saved);
    }

    @Override
    public void deleteUser(UUID id) {
        appUserRepository.deleteById(id);
    }

    @Override
    public List<AppUserDto> getAllUsers() {
        return appUserRepository.findAll().stream().map(AppUserDto::new).collect(Collectors.toList());
    }

}

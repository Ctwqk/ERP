package com.example.auth.service;

import com.example.auth.domain.AppRole;
import com.example.auth.domain.AppRole.RoleName;
import com.example.auth.domain.AppUser;
import com.example.auth.domain.AppUserRole;
import com.example.auth.dto.AppUserDto;
import com.example.auth.repository.AppRoleRepository;
import com.example.auth.repository.AppUserRepository;
import com.example.auth.repository.AppUserRoleRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this e-mail already exists");
        }
        AppUser user = new AppUser(userName, email, passwordEncoder.encode(password));
        AppRole defaultRole = appRoleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Default role USER is missing"));
        user.addUserRole(new AppUserRole(user, defaultRole));
        return new AppUserDto(appUserRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public AppUserDto getUserById(UUID id) {
        return new AppUserDto(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public AppUserDto getUserByEmail(String email) {
        return new AppUserDto(appUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppUserDto> searchUsers(String keyword, Boolean active, Pageable pageable) {
        return appUserRepository.search(keyword, active, pageable).map(AppUserDto::new);
    }

    @Override
    public void disableUser(UUID id) {
        find(id).setActive(false);
    }

    @Override
    public void enableUser(UUID id) {
        find(id).setActive(true);
    }

    @Override
    public void assignRoleToUser(UUID userId, UUID roleId) {
        AppUser user = find(userId);
        AppRole role = appRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        if (!appUserRoleRepository.existsByUserIdAndRoleName(userId, role.getName())) {
            user.addUserRole(new AppUserRole(user, role));
        }
    }

    @Override
    public void removeRoleFromUser(UUID userId, UUID roleId) {
        AppUser user = find(userId);
        user.getUserRoles().stream()
                .filter(ur -> ur.getRole().getId().equals(roleId))
                .findFirst()
                .ifPresentOrElse(user::removeUserRole,
                        () -> {
                            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User does not have this role");
                        });
    }

    @Override
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        AppUser user = find(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    @Override
    public AppUserDto updateUser(AppUserDto dto, boolean allowActiveChange) {
        if (dto.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User id is required");
        }
        AppUser user = find(dto.getId());
        if (dto.getEmail() != null && !dto.getEmail().equalsIgnoreCase(user.getEmail())
                && appUserRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this e-mail already exists");
        }
        if (dto.getName() != null) {
            user.setName(dto.getName());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getActive() != null && allowActiveChange) {
            user.setActive(dto.getActive());
        }
        return new AppUserDto(user);
    }

    @Override
    public void deleteUser(UUID id) {
        appUserRepository.delete(find(id));
    }

    private AppUser find(UUID id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}

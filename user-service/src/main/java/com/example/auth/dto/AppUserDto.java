package com.example.auth.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.example.auth.domain.AppUser;

public class AppUserDto {
    private UUID id;
    private String name;
    private String email;
    private Boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<AppRoleDto> userRoles;

    public AppUserDto() {
    }

    public AppUserDto(AppUser user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.active = user.getActive();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<AppRoleDto> getUserRoles() {
        return userRoles;
    }

    public void setUserRoles(List<AppRoleDto> userRoles) {
        this.userRoles = userRoles;
    }

    /**
     * Convenience: returns role names from userRoles.
     */
    public List<String> getRoles() {
        if (userRoles == null) {
            return List.of();
        }
        return userRoles.stream()
                .map(AppRoleDto::getName)
                .toList();
    }
}

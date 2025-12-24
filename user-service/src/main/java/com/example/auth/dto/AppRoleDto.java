package com.example.auth.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.example.auth.domain.AppRole;
import com.example.auth.domain.AppRole.RoleName;

public class AppRoleDto {
    private UUID id;
    private String name;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<AppUserDto> users;

    public AppRoleDto() {
    }

    public AppRoleDto(UUID id, RoleName name, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            List<AppUserDto> users) {
        this.id = id;
        this.name = name.name();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.users = users;
    }

    public AppRoleDto(AppRole role) {
        this.id = role.getId();
        this.name = role.getName().name();
        this.createdAt = role.getCreatedAt();
        this.updatedAt = role.getUpdatedAt();
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

    public List<AppUserDto> getUsers() {
        return users;
    }

    public void setUsers(List<AppUserDto> users) {
        this.users = users;
    }
}

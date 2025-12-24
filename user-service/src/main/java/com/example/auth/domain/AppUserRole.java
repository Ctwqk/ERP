package com.example.auth.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "app_user_roles", uniqueConstraints = {
        @UniqueConstraint(name = "ux_app_user_roles_user_role", columnNames = { "user_id", "role_id" })
}, indexes = {
        @Index(name = "idx_app_user_roles_user", columnList = "user_id"),
        @Index(name = "idx_app_user_roles_role", columnList = "role_id")
})
@IdClass(AppUserRole.AppUserRoleId.class)
public class AppUserRole {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private AppRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Constructors
    public AppUserRole() {
    }

    public AppUserRole(AppUser user, AppRole role) {
        this.user = user;
        this.role = role;
    }

    /**
     * Convenience constructor when only IDs are available (avoids loading
     * entities).
     */
    public AppUserRole(UUID userId, UUID roleId) {
        AppUser u = new AppUser();
        u.setId(userId);
        AppRole r = new AppRole();
        r.setId(roleId);
        this.user = u;
        this.role = r;
    }

    // Getters and Setters
    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public AppRole getRole() {
        return role;
    }

    public void setRole(AppRole role) {
        this.role = role;
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

    /**
     * Composite key for app_user_roles (user_id, role_id).
     */
    public static class AppUserRoleId implements Serializable {
        private UUID user;
        private UUID role;

        public AppUserRoleId() {
        }

        public AppUserRoleId(UUID user, UUID role) {
            this.user = user;
            this.role = role;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            AppUserRoleId that = (AppUserRoleId) o;
            return Objects.equals(user, that.user) && Objects.equals(role, that.role);
        }

        @Override
        public int hashCode() {
            return Objects.hash(user, role);
        }
    }
}

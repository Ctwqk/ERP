package com.example.auth.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static UUID currentUserIdOrThrow() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No JWT principal");
        }
        UUID uid = com.example.common.security.JwtUsers.userId(jwt);
        if (uid == null) {
            throw new IllegalStateException("Token has no uid claim");
        }
        return uid;
    }

    public static boolean hasAnyRole(String... roles) {
        List<String> mine = currentUserRolesOrThrow();
        for (String role : roles) {
            if (mine.contains(role)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> currentUserRolesOrThrow() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No JWT principal");
        }
        List<String> rawRoles = jwt.getClaimAsStringList("roles");
        if (rawRoles == null) {
            throw new IllegalStateException("No roles claim");
        }
        return rawRoles.stream()
                .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .toList();
    }
}

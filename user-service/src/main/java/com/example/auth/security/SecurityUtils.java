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
        String uid = jwt.getClaimAsString("uid");
        return UUID.fromString(uid);
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

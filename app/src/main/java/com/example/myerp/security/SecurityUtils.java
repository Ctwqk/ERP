package com.example.myerp.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static AuthUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser u)) {
            return null;
        }
        return u;
    }

    public static UUID currentUserIdOrThrow() {
        AuthUser u = currentUser();
        if (u == null)
            throw new IllegalStateException("No authenticated user");
        return u.getId();
    }
}

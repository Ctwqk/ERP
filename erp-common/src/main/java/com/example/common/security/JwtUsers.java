package com.example.common.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Helpers to read the caller identity from the access token issued by user-service.
 *
 * <p>Token layout: {@code sub} = user e-mail, {@code uid} = user UUID, {@code roles} = {@code ROLE_*} names.
 */
public final class JwtUsers {

    public static final String USER_ID_CLAIM = "uid";
    public static final String ROLES_CLAIM = "roles";

    private JwtUsers() {
    }

    /** The user's UUID ({@code uid} claim), or {@code null} when the token carries none. */
    public static UUID userId(Jwt jwt) {
        String uid = jwt.getClaimAsString(USER_ID_CLAIM);
        if (uid == null || uid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(uid);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** The token subject (the user's e-mail), or {@code null} if there is no JWT principal. */
    public static String subject(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return authentication != null ? authentication.getName() : null;
    }

    /** The user's UUID from the authentication, or {@code null} if absent. */
    public static UUID userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return userId(jwt);
        }
        return null;
    }
}

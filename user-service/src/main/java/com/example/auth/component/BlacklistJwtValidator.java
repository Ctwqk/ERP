package com.example.auth.component;

import com.example.auth.service.JwtBlacklistService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Rejects access tokens that were revoked through {@code POST /auth/logout}. */
@Component
public class BlacklistJwtValidator implements OAuth2TokenValidator<Jwt> {

    private final JwtBlacklistService blacklistService;

    public BlacklistJwtValidator(JwtBlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String jti = jwt.getId();
        if (jti != null && blacklistService.isBlacklisted(jti)) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token has been revoked", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}

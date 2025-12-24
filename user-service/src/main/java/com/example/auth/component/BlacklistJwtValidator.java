package com.example.auth.component;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.example.auth.service.JwtBlacklistService;

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
            OAuth2Error err = new OAuth2Error("invalid_token", "Token is blacklisted", null);
            return OAuth2TokenValidatorResult.failure(err);
        }
        return OAuth2TokenValidatorResult.success();
    }
}

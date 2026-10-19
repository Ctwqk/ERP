package com.example.auth.service;

import com.example.auth.domain.AppUser;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import com.example.auth.repository.AppUserRepository;
import com.example.auth.security.JwtIssuer;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final JwtBlacklistService jwtBlacklistService;
    private final JwtDecoder jwtDecoder;

    public AuthServiceImpl(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer,
            JwtBlacklistService jwtBlacklistService, JwtDecoder jwtDecoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.jwtBlacklistService = jwtBlacklistService;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // Same answer for "no such user" and "wrong password" so the endpoint does not reveal which e-mails exist.
        AppUser user = appUserRepository.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad credentials"));
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is disabled");
        }
        return new LoginResponse(jwtIssuer.issueToken(user.getId(), user.getEmail(),
                user.getRoles().stream().map(r -> "ROLE_" + r).toList()));
    }

    @Override
    public void logout(String token) {
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token is required");
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token); // also rejects expired, foreign and already-revoked tokens
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        String jti = jwt.getId();
        Instant exp = jwt.getExpiresAt();
        if (jti == null || exp == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token missing jti/exp");
        }
        // keep the revocation entry only until the token would have expired anyway
        Duration ttl = Duration.between(Instant.now(), exp);
        if (!ttl.isNegative() && !ttl.isZero()) {
            jwtBlacklistService.blacklist(jti, ttl);
        }
    }
}

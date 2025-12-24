package com.example.auth.service;

import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import com.example.auth.domain.AppUser;
import com.example.auth.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.auth.security.JwtIssuer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.Duration;

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
    public LoginResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new RuntimeException("Bad credentials");
        }
        return new LoginResponse(jwtIssuer.issueToken(user.getId(), user.getEmail(),
                user.getRoles().stream().map(r -> "ROLE_" + r).toList()));
    }

    @Override
    public void logout(String token) {
        if (token == null) {
            throw new RuntimeException("Token is required");
        }
        if (jwtBlacklistService.isBlacklisted(token)) {
            throw new RuntimeException("Token is already blacklisted");
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        String jti = jwt.getId(); // jti
        Instant exp = jwt.getExpiresAt(); // exp

        if (jti == null || exp == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token missing jti/exp");
        }

        Duration ttl = Duration.between(Instant.now(), exp);
        jwtBlacklistService.blacklist(jti, ttl);
    }
}

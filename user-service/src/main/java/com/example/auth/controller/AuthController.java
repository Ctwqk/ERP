package com.example.auth.controller;

import com.example.auth.domain.AppUser;
import com.example.auth.repository.AppUserRepository;
import com.example.auth.security.JwtIssuer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AppUserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtIssuer jwtIssuer;

    public AuthController(AppUserRepository userRepo, PasswordEncoder encoder, JwtIssuer jwtIssuer) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtIssuer = jwtIssuer;
    }

    public record LoginRequest(String email, String password) {
    }

    public record LoginResponse(String accessToken) {
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        AppUser u = userRepo.findByEmail(req.email())
                .orElseThrow(() -> new RuntimeException("Bad credentials"));

        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw new RuntimeException("Bad credentials");
        }

        // roles 统一输出 ROLE_ 前缀
        List<String> roles = u.getRoles().stream().map(r -> "ROLE_" + r).toList();

        String token = jwtIssuer.issueToken(u.getId(), u.getEmail(), roles);
        return new LoginResponse(token);
    }
}

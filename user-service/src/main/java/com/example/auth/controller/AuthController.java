package com.example.auth.controller;

import com.example.auth.domain.AppUser;
import com.example.auth.repository.AppUserRepository;
import com.example.auth.security.JwtIssuer;
import org.springframework.web.bind.annotation.*;
import com.example.auth.service.AuthService;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (!authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        authService.logout(token);
    }
}

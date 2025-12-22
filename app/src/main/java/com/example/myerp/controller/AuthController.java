package com.example.myerp.controller;

import com.example.myerp.api.dto.LoginRequest;
import com.example.myerp.api.dto.LoginResponse;
import com.example.myerp.security.AuthUser;
import com.example.myerp.security.JwtService;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));

        AuthUser user = (AuthUser) auth.getPrincipal();
        String token = jwtService.issueAccessToken(user);

        return new LoginResponse(token);
    }
}

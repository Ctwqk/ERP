package com.example.auth.controller;

import com.example.auth.security.JwksProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class JwksController {

    private final JwksProvider jwksProvider;

    public JwksController(JwksProvider jwksProvider) {
        this.jwksProvider = jwksProvider;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwksProvider.jwkSet().toJSONObject();
    }
}

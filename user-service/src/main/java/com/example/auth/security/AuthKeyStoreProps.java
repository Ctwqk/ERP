package com.example.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.keystore")
public record AuthKeyStoreProps(
        String location,
        String password,
        String alias,
        String keyPassword
) {}


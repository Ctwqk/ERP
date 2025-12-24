package com.example.auth.service;

import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);

    void logout(String token);
}

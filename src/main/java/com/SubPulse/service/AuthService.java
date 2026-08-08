package com.subpulse.service;

import com.subpulse.dto.request.LoginRequest;
import com.subpulse.dto.request.RegisterRequest;
import com.subpulse.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}

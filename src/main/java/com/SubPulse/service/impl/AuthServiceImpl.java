package com.subpulse.service.impl;

import com.subpulse.dto.request.LoginRequest;
import com.subpulse.dto.request.RegisterRequest;
import com.subpulse.dto.response.AuthResponse;
import com.subpulse.dto.response.UserResponse;
import com.subpulse.entity.User;
import com.subpulse.exception.DuplicateResourceException;
import com.subpulse.repository.UserRepository;
import com.subpulse.security.JwtUtils;
import com.subpulse.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository     userRepository;
    private final PasswordEncoder    passwordEncoder;
    private final JwtUtils           jwtUtils;
    private final AuthenticationManager authenticationManager;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .timezone(request.getTimezone())
                .preferredCurrency(request.getPreferredCurrency())
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // AuthenticationManager validates credentials — throws BadCredentialsException on failure
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();
        log.info("User logged in: {}", user.getEmail());

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtUtils.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtils.getExpirationMs() / 1000)
                .user(mapToUserResponse(user))
                .build();
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .timezone(user.getTimezone())
                .preferredCurrency(user.getPreferredCurrency())
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .oauth2Provider(user.getOauth2Provider())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

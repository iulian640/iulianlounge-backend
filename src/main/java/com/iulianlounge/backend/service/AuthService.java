package com.iulianlounge.backend.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.LoginRequest;
import com.iulianlounge.backend.dto.LoginResponse;
import com.iulianlounge.backend.dto.RefreshRequest;
import com.iulianlounge.backend.dto.RefreshResponse;
import com.iulianlounge.backend.exception.InvalidCredentialsException;
import com.iulianlounge.backend.exception.InvalidTokenException;
import com.iulianlounge.backend.repository.UserRepository;
import com.iulianlounge.backend.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return new LoginResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                JwtService.ACCESS_TTL.toSeconds());
    }

    // Stateless (ADR-08): el refresh viejo sigue valiendo hasta que caduca; no hay lista negra en BD
    public RefreshResponse refresh(RefreshRequest request) {
        UUID userId = jwtService.validateRefreshToken(request.refreshToken());

        // El token puede ser válido y la cuenta ya no existir
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("Token inválido o caducado"));

        return new RefreshResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user));
    }
}

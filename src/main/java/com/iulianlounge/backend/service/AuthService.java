package com.iulianlounge.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.LoginRequest;
import com.iulianlounge.backend.exception.InvalidCredentialsException;
import com.iulianlounge.backend.exception.InvalidTokenException;
import com.iulianlounge.backend.repository.UserRepository;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.security.RefreshTokenClaims;

@Service
public class AuthService {

    // Un token real ronda 300 caracteres: el tope evita que nos manden megas a decodificar
    private static final int MAX_REFRESH_TOKEN_LENGTH = 1024;
    private static final String INVALID_TOKEN = "Token inválido o caducado";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    public IssuedTokens login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        Instant now = clock.instant();
        return issue(user, now, now.plus(JwtService.REFRESH_TTL));
    }

    // Stateless (ADR-08): el refresh viejo sigue valiendo hasta que caduca; no hay lista negra en BD.
    // El nuevo hereda esa caducidad, así ninguna sesión pasa de 7 días desde el login.
    public IssuedTokens refresh(String refreshToken) {
        // Sin cookie (null) o con basura enorme: 401 como cualquier token malo, sin decodificar nada
        if (refreshToken == null || refreshToken.isBlank() || refreshToken.length() > MAX_REFRESH_TOKEN_LENGTH) {
            throw new InvalidTokenException(INVALID_TOKEN);
        }
        RefreshTokenClaims claims = jwtService.validateRefreshToken(refreshToken);

        // El token puede ser válido y la cuenta ya no existir
        User user = userRepository.findById(claims.userId())
                .orElseThrow(() -> new InvalidTokenException(INVALID_TOKEN));

        return issue(user, clock.instant(), claims.expiresAt());
    }

    // Un solo "ahora" por petición: la vida de la cookie se calcula con el mismo instante que el token
    private IssuedTokens issue(User user, Instant now, Instant refreshExpiresAt) {
        return new IssuedTokens(
                jwtService.generateAccessToken(user),
                JwtService.ACCESS_TTL,
                jwtService.generateRefreshToken(user, refreshExpiresAt),
                Duration.between(now, refreshExpiresAt));
    }
}

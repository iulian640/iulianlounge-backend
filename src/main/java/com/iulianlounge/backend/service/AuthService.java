package com.iulianlounge.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

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

    private static final int MAX_REFRESH_TOKEN_LENGTH = 1024;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public IssuedTokens login(LoginRequest request) {
        Optional<User> found = userRepository.findByUsername(request.username());
        String hash = found.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (found.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        User user = found.get();

        Instant now = clock.instant();
        return issue(user, now, now.plus(JwtService.REFRESH_TTL));
    }

    public IssuedTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank() || refreshToken.length() > MAX_REFRESH_TOKEN_LENGTH) {
            throw new InvalidTokenException();
        }
        RefreshTokenClaims claims = jwtService.validateRefreshToken(refreshToken);

        User user = userRepository.findById(claims.userId())
                .orElseThrow(() -> new InvalidTokenException());

        return issue(user, clock.instant(), claims.expiresAt());
    }

    private IssuedTokens issue(User user, Instant now, Instant refreshExpiresAt) {
        return new IssuedTokens(
                jwtService.generateAccessToken(user),
                JwtService.ACCESS_TTL,
                jwtService.generateRefreshToken(user, refreshExpiresAt),
                Duration.between(now, refreshExpiresAt));
    }
}

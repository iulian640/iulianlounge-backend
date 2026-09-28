package com.iulianlounge.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.LoginRequest;
import com.iulianlounge.backend.exception.ErrorCode;
import com.iulianlounge.backend.exception.InvalidCredentialsException;
import com.iulianlounge.backend.exception.InvalidTokenException;
import com.iulianlounge.backend.repository.UserRepository;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.security.RefreshTokenClaims;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash-de-relleno");
        authService = new AuthService(userRepository, passwordEncoder, jwtService, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("cursaito");
        user.setPasswordHash("hash-guardado");
    }

    @Test
    void loginReturnsTokensWhenPasswordMatches() {
        when(userRepository.findByUsername("cursaito")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("12345678", "hash-guardado")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user, NOW.plus(JwtService.REFRESH_TTL))).thenReturn("refresh-token");

        IssuedTokens tokens = authService.login(new LoginRequest("cursaito", "12345678"));

        assertEquals("access-token", tokens.accessToken());
        assertEquals(Duration.ofMinutes(15), tokens.accessTtl());
        assertEquals("refresh-token", tokens.refreshToken());
        assertEquals(Duration.ofDays(7), tokens.refreshTtl());
    }

    @Test
    void loginThrowsWhenPasswordIsWrong() {
        when(userRepository.findByUsername("cursaito")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mala-clave", "hash-guardado")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("cursaito", "mala-clave")));
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void loginThrowsSameErrorWhenUserDoesNotExist() {
        when(userRepository.findByUsername("nadie")).thenReturn(Optional.empty());

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("nadie", "12345678")));
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, ex.getErrorCode());
    }

    @Test
    void loginChecksPasswordEvenWhenUserDoesNotExist() {
        when(userRepository.findByUsername("nadie")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("nadie", "12345678")));
        verify(passwordEncoder).matches(eq("12345678"), anyString());
    }

    @Test
    void refreshReturnsNewTokenPairForValidRefreshToken() {
        Instant loginExpiry = NOW.plus(Duration.ofDays(5));
        when(jwtService.validateRefreshToken("refresh-valido"))
                .thenReturn(new RefreshTokenClaims(user.getId(), loginExpiry));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access-nuevo");
        when(jwtService.generateRefreshToken(user, loginExpiry)).thenReturn("refresh-nuevo");

        IssuedTokens tokens = authService.refresh("refresh-valido");

        assertEquals("access-nuevo", tokens.accessToken());
        assertEquals("refresh-nuevo", tokens.refreshToken());
        assertEquals(Duration.ofDays(5), tokens.refreshTtl());
    }

    @Test
    void refreshPropagatesInvalidTokenFromJwtService() {
        when(jwtService.validateRefreshToken("caducado"))
                .thenThrow(new InvalidTokenException());

        assertThrows(InvalidTokenException.class, () -> authService.refresh("caducado"));
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void refreshThrowsWhenUserNoLongerExists() {
        when(jwtService.validateRefreshToken("refresh-de-cuenta-borrada"))
                .thenReturn(new RefreshTokenClaims(user.getId(), NOW.plus(Duration.ofDays(5))));
        when(userRepository.findById(user.getId())).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> authService.refresh("refresh-de-cuenta-borrada"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void refreshRejectsMissingOrBlankCookieWithoutTouchingJwtService(String refreshToken) {
        assertThrows(InvalidTokenException.class, () -> authService.refresh(refreshToken));
        verify(jwtService, never()).validateRefreshToken(any());
    }

    @Test
    void refreshRejectsHugeTokenWithoutDecodingIt() {
        String huge = "a".repeat(1025);

        assertThrows(InvalidTokenException.class, () -> authService.refresh(huge));
        verify(jwtService, never()).validateRefreshToken(any());
    }

    @Test
    void refreshStillDecodesATokenOfExactly1024Characters() {
        String atLimit = "a".repeat(1024);
        when(jwtService.validateRefreshToken(atLimit)).thenThrow(new InvalidTokenException());

        assertThrows(InvalidTokenException.class, () -> authService.refresh(atLimit));
        verify(jwtService).validateRefreshToken(atLimit);
    }
}

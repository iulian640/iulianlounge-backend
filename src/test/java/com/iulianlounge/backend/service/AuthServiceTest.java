package com.iulianlounge.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.LoginRequest;
import com.iulianlounge.backend.dto.LoginResponse;
import com.iulianlounge.backend.dto.RefreshRequest;
import com.iulianlounge.backend.dto.RefreshResponse;
import com.iulianlounge.backend.exception.InvalidCredentialsException;
import com.iulianlounge.backend.exception.InvalidTokenException;
import com.iulianlounge.backend.repository.UserRepository;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.security.RefreshTokenClaims;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
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
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");

        LoginResponse response = authService.login(new LoginRequest("cursaito", "12345678"));

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals(900, response.expiresIn());   // 15 minutos
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
        assertEquals("Usuario o contraseña incorrectos", ex.getMessage());
    }

    @Test
    void refreshReturnsNewTokenPairForValidRefreshToken() {
        Instant loginExpiry = Instant.parse("2026-09-30T12:00:00Z");
        when(jwtService.validateRefreshToken("refresh-valido"))
                .thenReturn(new RefreshTokenClaims(user.getId(), loginExpiry));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access-nuevo");
        // El refresh nuevo se pide con la caducidad del original, no con 7 días nuevos
        when(jwtService.generateRefreshToken(user, loginExpiry)).thenReturn("refresh-nuevo");

        RefreshResponse response = authService.refresh(new RefreshRequest("refresh-valido"));

        assertEquals("access-nuevo", response.accessToken());
        assertEquals("refresh-nuevo", response.refreshToken());
    }

    @Test
    void refreshPropagatesInvalidTokenFromJwtService() {
        when(jwtService.validateRefreshToken("caducado"))
                .thenThrow(new InvalidTokenException("Token inválido o caducado"));

        assertThrows(InvalidTokenException.class,
                () -> authService.refresh(new RefreshRequest("caducado")));
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void refreshThrowsWhenUserNoLongerExists() {
        when(jwtService.validateRefreshToken("refresh-de-cuenta-borrada"))
                .thenReturn(new RefreshTokenClaims(user.getId(), Instant.parse("2026-09-30T12:00:00Z")));
        when(userRepository.findById(user.getId())).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class,
                () -> authService.refresh(new RefreshRequest("refresh-de-cuenta-borrada")));
    }
}

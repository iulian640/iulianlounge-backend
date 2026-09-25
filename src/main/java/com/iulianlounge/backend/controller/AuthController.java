package com.iulianlounge.backend.controller;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.iulianlounge.backend.dto.AccessTokenResponse;
import com.iulianlounge.backend.dto.LoginRequest;
import com.iulianlounge.backend.dto.RegisterRequest;
import com.iulianlounge.backend.dto.RegisterResponse;
import com.iulianlounge.backend.security.RefreshCookies;
import com.iulianlounge.backend.service.AuthService;
import com.iulianlounge.backend.service.IssuedTokens;
import com.iulianlounge.backend.service.RegisterService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterService registerService;
    private final AuthService authService;

    public AuthController(RegisterService registerService, AuthService authService) {
        this.registerService = registerService;
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        UUID userId = registerService.register(request);
        return new RegisterResponse(userId);
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    // ADR-08: el refresh llega en la cookie, no en el cuerpo. Sin cookie → null → el service responde 401
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    // Público: con el access ya caducado también tiene que poder salir. Idempotente
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, RefreshCookies.clear().toString())
                .build();
    }

    // El access va al cuerpo (el frontend lo guarda en memoria); el refresh, a la cookie HttpOnly
    private ResponseEntity<AccessTokenResponse> withRefreshCookie(IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        RefreshCookies.create(tokens.refreshToken(), tokens.refreshTtl()).toString())
                .body(new AccessTokenResponse(tokens.accessToken(), tokens.accessTtl().toSeconds()));
    }
}

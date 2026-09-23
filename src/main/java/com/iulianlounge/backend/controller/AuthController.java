package com.iulianlounge.backend.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.iulianlounge.backend.dto.LoginRequest;
import com.iulianlounge.backend.dto.LoginResponse;
import com.iulianlounge.backend.dto.RegisterRequest;
import com.iulianlounge.backend.dto.RegisterResponse;
import com.iulianlounge.backend.service.AuthService;
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
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}

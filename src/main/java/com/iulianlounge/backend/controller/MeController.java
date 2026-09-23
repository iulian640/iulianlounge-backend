package com.iulianlounge.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iulianlounge.backend.dto.MeResponse;
import com.iulianlounge.backend.security.AccessTokenClaims;
import com.iulianlounge.backend.service.UserService;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    // Solo llega aquí quien trae un access token válido: el filtro JWT puso sus claims como principal
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AccessTokenClaims claims) {
        return userService.getProfile(claims.userId());
    }
}

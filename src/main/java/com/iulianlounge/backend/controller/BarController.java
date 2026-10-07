package com.iulianlounge.backend.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.iulianlounge.backend.dto.BarResponse;
import com.iulianlounge.backend.dto.OrderRequest;
import com.iulianlounge.backend.dto.OrderResponse;
import com.iulianlounge.backend.security.AccessTokenClaims;
import com.iulianlounge.backend.service.BarService;

@RestController
@RequestMapping("/api/v1/bar")
public class BarController {

    private final BarService barService;

    public BarController(BarService barService) {
        this.barService = barService;
    }

    @GetMapping
    public BarResponse bar(@AuthenticationPrincipal(errorOnInvalidType = true) AccessTokenClaims claims) {
        return barService.menu(claims.userId());
    }

    @PostMapping("/orders")
    public OrderResponse order(@AuthenticationPrincipal(errorOnInvalidType = true) AccessTokenClaims claims,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody OrderRequest request) {
        return barService.order(claims.userId(), request.drink(), idempotencyKey);
    }
}

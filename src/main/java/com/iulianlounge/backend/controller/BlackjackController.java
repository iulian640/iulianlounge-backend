package com.iulianlounge.backend.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.iulianlounge.backend.dto.BlackjackResponse;
import com.iulianlounge.backend.dto.DealRequest;
import com.iulianlounge.backend.dto.HandResponse;
import com.iulianlounge.backend.security.AccessTokenClaims;
import com.iulianlounge.backend.service.BlackjackService;

@RestController
@RequestMapping("/api/v1/blackjack")
public class BlackjackController {

    private final BlackjackService blackjackService;

    public BlackjackController(BlackjackService blackjackService) {
        this.blackjackService = blackjackService;
    }

    @GetMapping
    public BlackjackResponse table(@AuthenticationPrincipal(errorOnInvalidType = true) AccessTokenClaims claims) {
        return blackjackService.table(claims.userId());
    }

    @PostMapping("/hands")
    public HandResponse deal(@AuthenticationPrincipal(errorOnInvalidType = true) AccessTokenClaims claims,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody DealRequest request) {
        return blackjackService.deal(claims.userId(), request.bet(), idempotencyKey);
    }

    @PostMapping("/hands/{id}/hit")
    public HandResponse hit(@AuthenticationPrincipal(errorOnInvalidType = true) AccessTokenClaims claims,
            @PathVariable("id") UUID handId) {
        return blackjackService.hit(claims.userId(), handId);
    }

    @PostMapping("/hands/{id}/stand")
    public HandResponse stand(@AuthenticationPrincipal(errorOnInvalidType = true) AccessTokenClaims claims,
            @PathVariable("id") UUID handId) {
        return blackjackService.stand(claims.userId(), handId);
    }
}

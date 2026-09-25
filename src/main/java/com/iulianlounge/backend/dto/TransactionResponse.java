package com.iulianlounge.backend.dto;

import java.time.Instant;
import java.util.UUID;

import com.iulianlounge.backend.domain.TokenTransaction;

// Una línea del historial. type es una clave (WELCOME_BONUS): el frontend la traduce (ADR-06)
public record TransactionResponse(UUID id, long amount, String type, long balanceAfter, Instant createdAt) {

    public static TransactionResponse from(TokenTransaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getAmount(), transaction.getType().name(),
                transaction.getBalanceAfter(), transaction.getCreatedAt());
    }
}

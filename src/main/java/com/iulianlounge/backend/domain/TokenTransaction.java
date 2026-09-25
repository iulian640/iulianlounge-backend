package com.iulianlounge.backend.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// ADR-04: una línea del ledger, la fuente de verdad del saldo. Append-only: se inserta y no se toca nunca.
// @Immutable hace que Hibernate ignore cualquier cambio: aunque alguien llamara a un setter, no habría UPDATE
@Entity
@Immutable
@Table(name = "token_transaction")
public class TokenTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID walletId;

    // Con signo: positivo entra, negativo sale
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    // Saldo justo después de este movimiento: el historial se lee sin recalcular nada
    private long balanceAfter;

    private String idempotencyKey;

    @Column(nullable = false)
    private Instant createdAt;

    protected TokenTransaction() {
        // Para JPA
    }

    public TokenTransaction(UUID walletId, long amount, TransactionType type, long balanceAfter,
            String idempotencyKey, Instant createdAt) {
        this.walletId = walletId;
        this.amount = amount;
        this.type = type;
        this.balanceAfter = balanceAfter;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public long getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

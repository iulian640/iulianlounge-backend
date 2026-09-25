package com.iulianlounge.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.iulianlounge.backend.domain.TokenTransaction;

// ADR-04: solo WalletService lo usa, y solo para insertar y leer (el ledger no se modifica)
public interface TokenTransactionRepository extends JpaRepository<TokenTransaction, UUID> {

    Optional<TokenTransaction> findByWalletIdAndIdempotencyKey(UUID walletId, String idempotencyKey);

    // Usa el índice (wallet_id, created_at DESC) de la V4
    Page<TokenTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);
}

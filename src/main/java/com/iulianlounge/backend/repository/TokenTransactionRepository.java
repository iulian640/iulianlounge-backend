package com.iulianlounge.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import com.iulianlounge.backend.domain.TokenTransaction;

// ADR-04: el ledger es append-only. Repository a secas (no JpaRepository) para no ofrecer delete ni deleteAll:
// solo se puede insertar y leer. Solo WalletService lo usa
public interface TokenTransactionRepository extends Repository<TokenTransaction, UUID> {

    TokenTransaction saveAndFlush(TokenTransaction transaction);

    Optional<TokenTransaction> findByWalletIdAndIdempotencyKey(UUID walletId, String idempotencyKey);

    // Usa el índice (wallet_id, created_at DESC, id DESC) de la V4
    Page<TokenTransaction> findByWalletIdOrderByCreatedAtDescIdDesc(UUID walletId, Pageable pageable);
}

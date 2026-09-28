package com.iulianlounge.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import com.iulianlounge.backend.domain.TokenTransaction;

public interface TokenTransactionRepository extends Repository<TokenTransaction, UUID> {

    TokenTransaction saveAndFlush(TokenTransaction transaction);

    Optional<TokenTransaction> findByWalletIdAndIdempotencyKey(UUID walletId, String idempotencyKey);

    Page<TokenTransaction> findByWalletIdOrderByCreatedAtDescIdDesc(UUID walletId, Pageable pageable);
}

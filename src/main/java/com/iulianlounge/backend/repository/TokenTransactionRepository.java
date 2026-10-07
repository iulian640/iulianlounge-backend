package com.iulianlounge.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.iulianlounge.backend.domain.TokenTransaction;
import com.iulianlounge.backend.domain.TransactionType;

public interface TokenTransactionRepository extends Repository<TokenTransaction, UUID> {

    TokenTransaction saveAndFlush(TokenTransaction transaction);

    Optional<TokenTransaction> findByWalletIdAndIdempotencyKey(UUID walletId, String idempotencyKey);

    Page<TokenTransaction> findByWalletIdOrderByCreatedAtDescIdDesc(UUID walletId, Pageable pageable);

    @Query("select coalesce(sum(t.amount), 0) from TokenTransaction t where t.walletId = :walletId and t.type = :type")
    long sumAmountByWalletIdAndType(@Param("walletId") UUID walletId, @Param("type") TransactionType type);
}

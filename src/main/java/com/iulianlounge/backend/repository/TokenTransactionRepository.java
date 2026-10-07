package com.iulianlounge.backend.repository;

import java.util.List;
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

    List<TokenTransaction> findByWalletIdAndTypeOrderByCreatedAtAscIdAsc(UUID walletId, TransactionType type);

    @Query("select coalesce(sum(t.amount), 0L) from TokenTransaction t where t.walletId = :walletId and t.type = :type")
    long sumAmountByWalletIdAndType(@Param("walletId") UUID walletId, @Param("type") TransactionType type);
}

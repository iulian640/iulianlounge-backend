package com.iulianlounge.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.iulianlounge.backend.domain.BlackjackHand;
import com.iulianlounge.backend.domain.HandStatus;

public interface BlackjackHandRepository extends Repository<BlackjackHand, UUID> {

    BlackjackHand saveAndFlush(BlackjackHand hand);

    Optional<BlackjackHand> findByIdAndUserId(UUID id, UUID userId);

    Optional<BlackjackHand> findByUserIdAndIdempotencyKey(UUID userId, UUID idempotencyKey);

    Optional<BlackjackHand> findByUserIdAndStatus(UUID userId, HandStatus status);

    @Query(value = "select cast(coalesce(sum(bet), 0) as bigint) from blackjack_hand where user_id = :userId",
            nativeQuery = true)
    long sumBetByUserId(@Param("userId") UUID userId);

    @Query("""
            select h from BlackjackHand h
            where h.userId = :userId
              and h.status <> com.iulianlounge.backend.domain.HandStatus.PLAYER_TURN
              and h.settledAt is null
            """)
    List<BlackjackHand> findUnsettledByUserId(@Param("userId") UUID userId);
}

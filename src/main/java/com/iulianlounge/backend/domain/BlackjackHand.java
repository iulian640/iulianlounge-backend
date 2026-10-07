package com.iulianlounge.backend.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "blackjack_hand")
public class BlackjackHand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Column(nullable = false, updatable = false)
    private Bet bet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HandStatus status;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    private Long payout;

    @Convert(converter = CardsConverter.class)
    @Column(nullable = false)
    private List<Card> deck;

    @Convert(converter = CardsConverter.class)
    @Column(nullable = false)
    private List<Card> playerCards;

    @Convert(converter = CardsConverter.class)
    @Column(nullable = false)
    private List<Card> dealerCards;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant finishedAt;

    private Instant settledAt;

    protected BlackjackHand() {
    }

    public BlackjackHand(UUID userId, UUID idempotencyKey, Bet bet, BlackjackTable table, Instant now) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.bet = bet;
        this.createdAt = now;
        this.status = HandStatus.PLAYER_TURN;
        play(table, now);
    }

    private BlackjackHand(UUID userId, UUID idempotencyKey, Bet bet, Instant now) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.bet = bet;
        this.createdAt = now;
        this.status = HandStatus.VOID;
        this.payout = bet.chips();
        this.deck = List.of();
        this.playerCards = List.of();
        this.dealerCards = List.of();
        this.finishedAt = now;
    }

    public static BlackjackHand voided(UUID userId, UUID idempotencyKey, Bet bet, Instant now) {
        return new BlackjackHand(userId, idempotencyKey, bet, now);
    }

    public void play(BlackjackTable table, Instant now) {
        this.deck = table.deck();
        this.playerCards = table.playerCards();
        this.dealerCards = table.dealerCards();
        if (table.isFinished()) {
            this.status = HandStatus.FINISHED;
            this.outcome = table.outcome();
            this.payout = table.outcome().payoutFor(bet);
            this.finishedAt = now;
        }
    }

    public void settle(Instant now) {
        this.settledAt = now;
    }

    public BlackjackTable table() {
        return new BlackjackTable(deck, playerCards, dealerCards, outcome);
    }

    public boolean isSettled() {
        return settledAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public Bet getBet() {
        return bet;
    }

    public HandStatus getStatus() {
        return status;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public Long getPayout() {
        return payout;
    }

    public List<Card> getPlayerCards() {
        return playerCards;
    }

    public List<Card> getDealerCards() {
        return dealerCards;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}

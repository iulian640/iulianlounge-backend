package com.iulianlounge.backend.domain;

public enum Outcome {
    BLACKJACK(5, 2),
    WIN(2, 1),
    PUSH(1, 1),
    LOSE(0, 1);

    private final long numerator;
    private final long denominator;

    Outcome(long numerator, long denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public long payoutFor(Bet bet) {
        return bet.chips() * numerator / denominator;
    }
}

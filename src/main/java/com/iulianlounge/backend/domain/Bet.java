package com.iulianlounge.backend.domain;

import java.util.Arrays;

public enum Bet {
    TEN(10),
    TWENTY(20),
    FIFTY(50);

    private final long chips;

    Bet(long chips) {
        this.chips = chips;
    }

    public long chips() {
        return chips;
    }

    public static Bet fromChips(long chips) {
        return Arrays.stream(values())
                .filter(bet -> bet.chips == chips)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Not a bet: " + chips));
    }
}

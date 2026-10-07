package com.iulianlounge.backend.domain;

import java.util.Arrays;
import java.util.Optional;

public enum Suit {
    CLUBS('C'),
    DIAMONDS('D'),
    HEARTS('H'),
    SPADES('S');

    private final char code;

    Suit(char code) {
        this.code = code;
    }

    public char code() {
        return code;
    }

    static Optional<Suit> fromCode(char code) {
        return Arrays.stream(values()).filter(suit -> suit.code == code).findFirst();
    }
}

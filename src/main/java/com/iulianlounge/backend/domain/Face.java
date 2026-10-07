package com.iulianlounge.backend.domain;

import java.util.Arrays;
import java.util.Optional;

public enum Face {
    ACE('A', 1),
    TWO('2', 2),
    THREE('3', 3),
    FOUR('4', 4),
    FIVE('5', 5),
    SIX('6', 6),
    SEVEN('7', 7),
    EIGHT('8', 8),
    NINE('9', 9),
    TEN('T', 10),
    JACK('J', 10),
    QUEEN('Q', 10),
    KING('K', 10);

    private final char code;
    private final int points;

    Face(char code, int points) {
        this.code = code;
        this.points = points;
    }

    public char code() {
        return code;
    }

    public int points() {
        return points;
    }

    static Optional<Face> fromCode(char code) {
        return Arrays.stream(values()).filter(face -> face.code == code).findFirst();
    }
}

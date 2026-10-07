package com.iulianlounge.backend.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record Card(Face face, Suit suit) {

    private static final int CODE_LENGTH = 2;

    public Card {
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(suit, "suit");
    }

    public static List<Card> deck() {
        return Arrays.stream(Suit.values())
                .flatMap(suit -> Arrays.stream(Face.values()).map(face -> new Card(face, suit)))
                .toList();
    }

    public static Card fromCode(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            throw new IllegalArgumentException("Not a card code");
        }
        Face face = Face.fromCode(code.charAt(0)).orElseThrow(() -> new IllegalArgumentException("Not a card code"));
        Suit suit = Suit.fromCode(code.charAt(1)).orElseThrow(() -> new IllegalArgumentException("Not a card code"));
        return new Card(face, suit);
    }

    public String code() {
        return "" + face.code() + suit.code();
    }
}

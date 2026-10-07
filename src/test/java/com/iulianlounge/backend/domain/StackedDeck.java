package com.iulianlounge.backend.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class StackedDeck {

    private StackedDeck() {
    }

    public static List<Card> startingWith(String... codes) {
        List<Card> top = Arrays.stream(codes).map(Card::fromCode).toList();
        List<Card> deck = new ArrayList<>(top);
        Card.deck().stream().filter(card -> !top.contains(card)).forEach(deck::add);
        return List.copyOf(deck);
    }

    public static Shuffler shuffler(String... codes) {
        return deck -> startingWith(codes);
    }
}

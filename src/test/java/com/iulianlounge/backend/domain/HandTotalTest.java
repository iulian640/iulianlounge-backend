package com.iulianlounge.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class HandTotalTest {

    @Test
    void anAceCountsElevenWhileThatDoesNotBust() {
        HandTotal total = HandTotal.of(cards("AS", "6D"));

        assertEquals(17, total.value());
        assertTrue(total.soft());
    }

    @Test
    void anAceDropsToOneWhenElevenWouldBust() {
        HandTotal total = HandTotal.of(cards("AS", "6D", "KH"));

        assertEquals(17, total.value());
        assertFalse(total.soft());
    }

    @Test
    void twoAcesAreATwelveSoft() {
        HandTotal total = HandTotal.of(cards("AS", "AD"));

        assertEquals(12, total.value());
        assertTrue(total.soft());
    }

    @Test
    void aceFiveKingIsASixteenHard() {
        HandTotal total = HandTotal.of(cards("AS", "5D", "KH"));

        assertEquals(16, total.value());
        assertFalse(total.soft());
    }

    @Test
    void faceCardsAndTensCountTen() {
        assertEquals(30, HandTotal.of(cards("TS", "JD", "QH")).value());
        assertEquals(10, HandTotal.of(cards("KC")).value());
    }

    @Test
    void numberedCardsCountTheirFace() {
        assertEquals(2 + 3 + 4 + 5 + 6 + 7 + 8 + 9,
                HandTotal.of(cards("2C", "3C", "4C", "5C", "6C", "7C", "8C", "9C")).value());
    }

    @Test
    void anAceAndATenCardIsABlackjack() {
        assertTrue(HandTotal.of(cards("AS", "KD")).isBlackjack());
        assertTrue(HandTotal.of(cards("TH", "AC")).isBlackjack());
    }

    @Test
    void aThreeCardTwentyOneIsNotABlackjack() {
        HandTotal total = HandTotal.of(cards("7S", "7D", "7H"));

        assertEquals(21, total.value());
        assertFalse(total.isBlackjack());
    }

    @Test
    void twoCardsThatAreNotTwentyOneAreNotABlackjack() {
        assertFalse(HandTotal.of(cards("KS", "QD")).isBlackjack());
    }

    @Test
    void overTwentyOneIsABust() {
        assertTrue(HandTotal.of(cards("KS", "QD", "2H")).isBust());
        assertFalse(HandTotal.of(cards("KS", "QD", "AH")).isBust());
        assertFalse(HandTotal.of(cards("KS", "AD")).isBust());
    }

    @Test
    void anEmptyHandIsWorthNothing() {
        HandTotal total = HandTotal.of(List.of());

        assertEquals(0, total.value());
        assertFalse(total.soft());
        assertFalse(total.isBlackjack());
    }

    private static List<Card> cards(String... codes) {
        return Arrays.stream(codes).map(Card::fromCode).toList();
    }
}

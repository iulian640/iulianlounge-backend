package com.iulianlounge.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CardTest {

    @Test
    void aDeckHasFiftyTwoDistinctCards() {
        List<Card> deck = Card.deck();

        assertEquals(52, deck.size());
        assertEquals(52, new HashSet<>(deck).size());
    }

    @Test
    void theDeckAlwaysComesInTheSameOrder() {
        List<Card> deck = Card.deck();

        assertEquals(new Card(Face.ACE, Suit.CLUBS), deck.get(0));
        assertEquals(new Card(Face.KING, Suit.CLUBS), deck.get(12));
        assertEquals(new Card(Face.ACE, Suit.DIAMONDS), deck.get(13));
        assertEquals(new Card(Face.KING, Suit.SPADES), deck.get(51));
        assertEquals(deck, Card.deck());
    }

    @Test
    void everyCallReturnsANewList() {
        List<Card> first = Card.deck();
        List<Card> second = Card.deck();

        assertNotSame(first, second);
    }

    @Test
    void aCardKnowsItsTwoLetterCode() {
        assertEquals("AS", new Card(Face.ACE, Suit.SPADES).code());
        assertEquals("TD", new Card(Face.TEN, Suit.DIAMONDS).code());
        assertEquals("9C", new Card(Face.NINE, Suit.CLUBS).code());
        assertEquals("QH", new Card(Face.QUEEN, Suit.HEARTS).code());
    }

    @Test
    void theCodeOfEveryCardOfTheDeckReadsBackAsTheSameCard() {
        for (Card card : Card.deck()) {
            assertEquals(card, Card.fromCode(card.code()));
        }
    }

    @Test
    void readsCodesBack() {
        assertEquals(new Card(Face.ACE, Suit.SPADES), Card.fromCode("AS"));
        assertEquals(new Card(Face.TEN, Suit.DIAMONDS), Card.fromCode("TD"));
        assertEquals(new Card(Face.NINE, Suit.CLUBS), Card.fromCode("9C"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "A", "ASS", "1S", "AX", "as", "10S", "ZZ"})
    void anUnknownCodeIsRejected(String code) {
        assertThrows(IllegalArgumentException.class, () -> Card.fromCode(code));
    }

    @Test
    void aNullCodeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Card.fromCode(null));
    }
}

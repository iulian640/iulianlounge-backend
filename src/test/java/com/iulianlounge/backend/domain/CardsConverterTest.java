package com.iulianlounge.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class CardsConverterTest {

    private final CardsConverter converter = new CardsConverter();

    @Test
    void cardsAreStoredAsCommaSeparatedCodes() {
        List<Card> cards = List.of(Card.fromCode("AS"), Card.fromCode("TD"), Card.fromCode("9C"));

        assertEquals("AS,TD,9C", converter.convertToDatabaseColumn(cards));
    }

    @Test
    void aListOfCardsRoundTrips() {
        List<Card> cards = List.of(Card.fromCode("AS"), Card.fromCode("TD"), Card.fromCode("9C"));

        assertEquals(cards, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(cards)));
    }

    @Test
    void aWholeDeckRoundTrips() {
        List<Card> deck = Card.deck();

        assertEquals(deck, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(deck)));
    }

    @Test
    void anEmptyListIsAnEmptyString() {
        assertEquals("", converter.convertToDatabaseColumn(List.of()));
        assertEquals(List.of(), converter.convertToEntityAttribute(""));
    }

    @Test
    void aMissingValueReadsAsNoCards() {
        assertEquals(List.of(), converter.convertToEntityAttribute(null));
        assertEquals("", converter.convertToDatabaseColumn(null));
    }
}

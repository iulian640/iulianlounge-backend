package com.iulianlounge.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BetConverterTest {

    private final BetConverter converter = new BetConverter();

    @ParameterizedTest
    @EnumSource(Bet.class)
    void aBetRoundTripsThroughItsChips(Bet bet) {
        Long column = converter.convertToDatabaseColumn(bet);

        assertEquals(bet.chips(), column);
        assertEquals(bet, converter.convertToEntityAttribute(column));
    }

    @Test
    void nullStaysNull() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}

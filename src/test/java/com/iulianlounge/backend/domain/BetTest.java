package com.iulianlounge.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class BetTest {

    @Test
    void theChipsAreTenTwentyAndFifty() {
        assertEquals(List.of(10L, 20L, 50L), Arrays.stream(Bet.values()).map(Bet::chips).toList());
    }

    @ParameterizedTest
    @EnumSource(Bet.class)
    void everyBetIsEvenSoThreeToTwoIsAWholeNumber(Bet bet) {
        assertEquals(0, bet.chips() % 2);
        assertEquals(0, bet.chips() * 5 % 2);
    }

    @ParameterizedTest
    @EnumSource(Bet.class)
    void aBetReadsBackFromItsChips(Bet bet) {
        assertEquals(bet, Bet.fromChips(bet.chips()));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 15, 30, 100, -10})
    void anAmountThatIsNotABetIsRejected(long chips) {
        assertThrows(IllegalArgumentException.class, () -> Bet.fromChips(chips));
    }
}

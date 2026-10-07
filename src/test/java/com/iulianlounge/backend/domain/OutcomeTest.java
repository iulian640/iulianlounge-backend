package com.iulianlounge.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OutcomeTest {

    @ParameterizedTest
    @EnumSource(Bet.class)
    void aBlackjackPaysTwoAndAHalfTimesTheBet(Bet bet) {
        assertEquals(bet.chips() * 5 / 2, Outcome.BLACKJACK.payoutFor(bet));
        assertEquals(0, bet.chips() * 5 % 2);
    }

    @ParameterizedTest
    @EnumSource(Bet.class)
    void aWinPaysDoubleThePushReturnsTheBetAndALoseNothing(Bet bet) {
        assertEquals(bet.chips() * 2, Outcome.WIN.payoutFor(bet));
        assertEquals(bet.chips(), Outcome.PUSH.payoutFor(bet));
        assertEquals(0, Outcome.LOSE.payoutFor(bet));
    }

    @Test
    void theFiftyChipBetPaysOneTwentyFiveOnABlackjack() {
        assertEquals(125, Outcome.BLACKJACK.payoutFor(Bet.FIFTY));
        assertEquals(25, Outcome.BLACKJACK.payoutFor(Bet.TEN));
    }
}

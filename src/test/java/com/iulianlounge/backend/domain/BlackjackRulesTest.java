package com.iulianlounge.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class BlackjackRulesTest {

    @Test
    void theDealGoesPlayerDealerUpPlayerDealerHole() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("9S", "KD", "8H", "5C", "2C"));

        assertEquals(cards("9S", "8H"), table.playerCards());
        assertEquals(cards("KD", "5C"), table.dealerCards());
        assertEquals(48, table.deck().size());
        assertEquals(Card.fromCode("2C"), table.deck().get(0));
        assertNull(table.outcome());
        assertFalse(table.isFinished());
    }

    @Test
    void aDeckTooSmallToDealIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BlackjackRules.deal(cards("9S", "KD", "8H")));
    }

    @Test
    void aDealerNaturalEndsTheDealAsALoss() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("9S", "AD", "8H", "KC"));

        assertEquals(Outcome.LOSE, table.outcome());
        assertTrue(table.isFinished());
    }

    @Test
    void bothNaturalsArePushed() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("AS", "AD", "KS", "KD"));

        assertEquals(Outcome.PUSH, table.outcome());
    }

    @Test
    void aPlayerNaturalAgainstADealerWithoutOneIsABlackjack() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("AS", "9D", "KS", "8D"));

        assertEquals(Outcome.BLACKJACK, table.outcome());
    }

    @Test
    void aPlayerNaturalAgainstADealerAceShowingStillPaysWhenTheHoleCardIsNotATen() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("AS", "AD", "KS", "5D"));

        assertEquals(Outcome.BLACKJACK, table.outcome());
    }

    @Test
    void twentyOneInThreeCardsIsNotANatural() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("5S", "9D", "6H", "8D", "TC"));

        BlackjackTable hit = BlackjackRules.hit(table);

        assertEquals(21, HandTotal.of(hit.playerCards()).value());
        assertNull(hit.outcome());
    }

    @Test
    void hitTakesTheTopCardOfTheDeck() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("9S", "KD", "8H", "5C", "2C", "3C"));

        BlackjackTable hit = BlackjackRules.hit(table);

        assertEquals(cards("9S", "8H", "2C"), hit.playerCards());
        assertEquals(47, hit.deck().size());
        assertEquals(Card.fromCode("3C"), hit.deck().get(0));
        assertEquals(table.dealerCards(), hit.dealerCards());
        assertNull(hit.outcome());
    }

    @Test
    void bustingEndsTheHandAsALossWithoutTheDealerDrawing() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("TS", "KD", "6H", "5C", "KC"));

        BlackjackTable hit = BlackjackRules.hit(table);

        assertEquals(Outcome.LOSE, hit.outcome());
        assertEquals(26, HandTotal.of(hit.playerCards()).value());
        assertEquals(2, hit.dealerCards().size());
    }

    @Test
    void standMakesTheDealerDrawBelowSeventeen() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("TS", "6D", "9H", "5C", "4D", "3H", "2S"));

        BlackjackTable stood = BlackjackRules.stand(table);

        assertEquals(cards("6D", "5C", "4D", "3H"), stood.dealerCards());
        assertEquals(18, HandTotal.of(stood.dealerCards()).value());
        assertEquals(Outcome.WIN, stood.outcome());
        assertEquals(Card.fromCode("2S"), stood.deck().get(0));
    }

    @Test
    void theDealerStandsOnASoftSeventeen() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("TS", "AD", "8H", "6C", "2S"));

        BlackjackTable stood = BlackjackRules.stand(table);

        assertEquals(2, stood.dealerCards().size());
        assertEquals(Outcome.WIN, stood.outcome());
    }

    @Test
    void theDealerStandsOnAHardSeventeen() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("TS", "TD", "8H", "7C", "2S"));

        BlackjackTable stood = BlackjackRules.stand(table);

        assertEquals(2, stood.dealerCards().size());
        assertEquals(Outcome.WIN, stood.outcome());
    }

    @Test
    void theDealerKeepsDrawingOnASoftSixteen() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("TS", "AD", "8H", "5C", "2S"));

        BlackjackTable stood = BlackjackRules.stand(table);

        assertEquals(cards("AD", "5C", "2S"), stood.dealerCards());
        assertEquals(Outcome.PUSH, stood.outcome());
    }

    @Test
    void aDealerBustIsAWinForThePlayer() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("TS", "TD", "7H", "6C", "KH"));

        BlackjackTable stood = BlackjackRules.stand(table);

        assertTrue(HandTotal.of(stood.dealerCards()).isBust());
        assertEquals(Outcome.WIN, stood.outcome());
    }

    @Test
    void aHigherPlayerTotalWins() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("TS", "TD", "9H", "8C"));

        assertEquals(Outcome.WIN, BlackjackRules.stand(table).outcome());
    }

    @Test
    void anEqualTotalIsAPush() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("TS", "TD", "8H", "8C"));

        assertEquals(Outcome.PUSH, BlackjackRules.stand(table).outcome());
    }

    @Test
    void aLowerPlayerTotalLoses() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("TS", "TD", "7H", "9C"));

        assertEquals(Outcome.LOSE, BlackjackRules.stand(table).outcome());
    }

    @Test
    void aPlayerTwentyOneInThreeCardsBeatsADealerTwenty() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("5S", "TD", "6H", "KC", "TC"));

        BlackjackTable stood = BlackjackRules.stand(BlackjackRules.hit(table));

        assertEquals(Outcome.WIN, stood.outcome());
    }

    @Test
    void hitOnAFinishedTableThrows() {
        BlackjackTable finished = BlackjackRules.deal(StackedDeck.startingWith("AS", "9D", "KS", "8D"));

        assertThrows(IllegalStateException.class, () -> BlackjackRules.hit(finished));
    }

    @Test
    void standOnAFinishedTableThrows() {
        BlackjackTable played = BlackjackRules.stand(
                BlackjackRules.deal(StackedDeck.startingWith("TS", "TD", "8H", "8C")));

        assertThrows(IllegalStateException.class, () -> BlackjackRules.stand(played));
        assertThrows(IllegalStateException.class, () -> BlackjackRules.hit(played));
    }

    @Test
    void theTableGivenIsNeverMutated() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("5S", "6D", "9H", "5C", "4D", "3H"));
        BlackjackTable snapshot = new BlackjackTable(table.deck(), table.playerCards(), table.dealerCards(),
                table.outcome());

        BlackjackRules.hit(table);
        BlackjackRules.stand(table);

        assertEquals(snapshot, table);
        assertEquals(48, table.deck().size());
        assertEquals(2, table.playerCards().size());
        assertEquals(2, table.dealerCards().size());
    }

    @Test
    void theListsOfATableCannotBeChanged() {
        BlackjackTable table = BlackjackRules.deal(StackedDeck.startingWith("9S", "KD", "8H", "5C"));

        assertThrows(UnsupportedOperationException.class, () -> table.playerCards().add(Card.fromCode("2C")));
        assertThrows(UnsupportedOperationException.class, () -> table.deck().remove(0));
    }

    private static List<Card> cards(String... codes) {
        return Arrays.stream(codes).map(Card::fromCode).toList();
    }
}

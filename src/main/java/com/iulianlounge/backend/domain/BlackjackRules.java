package com.iulianlounge.backend.domain;

import java.util.ArrayList;
import java.util.List;

public final class BlackjackRules {

    private static final int OPENING_CARDS = 4;
    private static final int DEALER_STANDS_ON = 17;

    private BlackjackRules() {
    }

    public static BlackjackTable deal(List<Card> shuffledDeck) {
        if (shuffledDeck.size() < OPENING_CARDS) {
            throw new IllegalArgumentException("A deal needs at least " + OPENING_CARDS + " cards");
        }
        List<Card> player = List.of(shuffledDeck.get(0), shuffledDeck.get(2));
        List<Card> dealer = List.of(shuffledDeck.get(1), shuffledDeck.get(3));
        List<Card> rest = shuffledDeck.subList(OPENING_CARDS, shuffledDeck.size());
        return new BlackjackTable(rest, player, dealer, outcomeOfTheDeal(player, dealer));
    }

    public static BlackjackTable hit(BlackjackTable table) {
        requireInPlay(table);
        List<Card> player = new ArrayList<>(table.playerCards());
        player.add(table.deck().get(0));
        List<Card> rest = table.deck().subList(1, table.deck().size());
        Outcome outcome = HandTotal.of(player).isBust() ? Outcome.LOSE : null;
        return new BlackjackTable(rest, player, table.dealerCards(), outcome);
    }

    public static BlackjackTable stand(BlackjackTable table) {
        requireInPlay(table);
        List<Card> dealer = new ArrayList<>(table.dealerCards());
        List<Card> deck = table.deck();
        while (HandTotal.of(dealer).value() < DEALER_STANDS_ON) {
            dealer.add(deck.get(0));
            deck = deck.subList(1, deck.size());
        }
        return new BlackjackTable(deck, table.playerCards(), dealer, compare(table.playerCards(), dealer));
    }

    private static Outcome outcomeOfTheDeal(List<Card> player, List<Card> dealer) {
        boolean playerNatural = HandTotal.of(player).isBlackjack();
        boolean dealerNatural = HandTotal.of(dealer).isBlackjack();
        if (playerNatural && dealerNatural) {
            return Outcome.PUSH;
        }
        if (dealerNatural) {
            return Outcome.LOSE;
        }
        return playerNatural ? Outcome.BLACKJACK : null;
    }

    private static Outcome compare(List<Card> player, List<Card> dealer) {
        HandTotal dealerTotal = HandTotal.of(dealer);
        if (dealerTotal.isBust()) {
            return Outcome.WIN;
        }
        int playerValue = HandTotal.of(player).value();
        if (playerValue > dealerTotal.value()) {
            return Outcome.WIN;
        }
        return playerValue == dealerTotal.value() ? Outcome.PUSH : Outcome.LOSE;
    }

    private static void requireInPlay(BlackjackTable table) {
        if (table.isFinished()) {
            throw new IllegalStateException("The hand is already finished");
        }
    }
}

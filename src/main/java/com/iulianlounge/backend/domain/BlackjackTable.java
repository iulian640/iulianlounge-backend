package com.iulianlounge.backend.domain;

import java.util.List;

public record BlackjackTable(List<Card> deck, List<Card> playerCards, List<Card> dealerCards, Outcome outcome) {

    public BlackjackTable {
        deck = List.copyOf(deck);
        playerCards = List.copyOf(playerCards);
        dealerCards = List.copyOf(dealerCards);
    }

    public boolean isFinished() {
        return outcome != null;
    }

    @Override
    public String toString() {
        return "BlackjackTable";
    }
}

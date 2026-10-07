package com.iulianlounge.backend.dto;

import java.util.List;
import java.util.UUID;

import com.iulianlounge.backend.domain.BlackjackHand;
import com.iulianlounge.backend.domain.Card;
import com.iulianlounge.backend.domain.HandStatus;
import com.iulianlounge.backend.domain.HandTotal;
import com.iulianlounge.backend.domain.Outcome;

public record HandView(UUID id, long bet, HandStatus status, List<Card> playerCards, int playerTotal,
        List<Card> dealerCards, int dealerTotal, Outcome outcome, Long payout) {

    public static HandView of(BlackjackHand hand) {
        List<Card> dealerCards = hand.getStatus() == HandStatus.PLAYER_TURN
                ? hand.getDealerCards().subList(0, 1)
                : hand.getDealerCards();
        return new HandView(hand.getId(), hand.getBet().chips(), hand.getStatus(),
                hand.getPlayerCards(), HandTotal.of(hand.getPlayerCards()).value(),
                List.copyOf(dealerCards), HandTotal.of(dealerCards).value(),
                hand.getOutcome(), hand.getPayout());
    }
}

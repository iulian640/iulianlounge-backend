package com.iulianlounge.backend.domain;

import java.util.List;

public record HandTotal(int value, boolean soft, int cardCount) {

    public static final int TWENTY_ONE = 21;

    private static final int ACE_BONUS = 10;
    private static final int BLACKJACK_CARDS = 2;

    public static HandTotal of(List<Card> cards) {
        int hard = cards.stream().mapToInt(card -> card.face().points()).sum();
        boolean hasAce = cards.stream().anyMatch(card -> card.face() == Face.ACE);
        boolean soft = hasAce && hard + ACE_BONUS <= TWENTY_ONE;
        return new HandTotal(soft ? hard + ACE_BONUS : hard, soft, cards.size());
    }

    public boolean isBust() {
        return value > TWENTY_ONE;
    }

    public boolean isBlackjack() {
        return cardCount == BLACKJACK_CARDS && value == TWENTY_ONE;
    }
}

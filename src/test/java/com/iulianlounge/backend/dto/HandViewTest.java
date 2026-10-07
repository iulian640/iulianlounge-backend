package com.iulianlounge.backend.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.iulianlounge.backend.domain.Bet;
import com.iulianlounge.backend.domain.BlackjackHand;
import com.iulianlounge.backend.domain.BlackjackRules;
import com.iulianlounge.backend.domain.Card;
import com.iulianlounge.backend.domain.HandStatus;
import com.iulianlounge.backend.domain.Outcome;
import com.iulianlounge.backend.domain.StackedDeck;

class HandViewTest {

    private static final Instant NOW = Instant.parse("2026-10-07T20:00:00Z");

    @Test
    void whileThePlayerPlaysTheDealerShowsOneCardAndItsValue() {
        BlackjackHand hand = hand(Bet.TWENTY, "9S", "KD", "8H", "5C");

        HandView view = HandView.of(hand);

        assertEquals(List.of(Card.fromCode("KD")), view.dealerCards());
        assertEquals(10, view.dealerTotal());
        assertEquals(List.of(Card.fromCode("9S"), Card.fromCode("8H")), view.playerCards());
        assertEquals(17, view.playerTotal());
        assertEquals(HandStatus.PLAYER_TURN, view.status());
        assertEquals(20, view.bet());
        assertNull(view.outcome());
        assertNull(view.payout());
    }

    @Test
    void anAceShowingCountsElevenForTheDealer() {
        HandView view = HandView.of(hand(Bet.TEN, "9S", "AD", "8H", "5C"));

        assertEquals(11, view.dealerTotal());
    }

    @Test
    void onceTheHandIsFinishedTheDealerShowsEveryCard() {
        BlackjackHand hand = hand(Bet.TWENTY, "TS", "6D", "9H", "5C", "4D", "3H");
        hand.play(BlackjackRules.stand(hand.table()), NOW);

        HandView view = HandView.of(hand);

        assertEquals(4, view.dealerCards().size());
        assertEquals(18, view.dealerTotal());
        assertEquals(HandStatus.FINISHED, view.status());
        assertEquals(Outcome.WIN, view.outcome());
        assertEquals(40L, view.payout());
    }

    @Test
    void aNaturalIsShownFinishedWithBothDealerCards() {
        HandView view = HandView.of(hand(Bet.FIFTY, "AS", "9D", "KS", "8D"));

        assertEquals(2, view.dealerCards().size());
        assertEquals(Outcome.BLACKJACK, view.outcome());
        assertEquals(125L, view.payout());
    }

    @Test
    void theRecordHasNoComponentForTheDeckOrTheHoleCard() {
        List<String> components = Arrays.stream(HandView.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();

        assertEquals(List.of("id", "bet", "status", "playerCards", "playerTotal", "dealerCards", "dealerTotal",
                "outcome", "payout"), components);
    }

    private static BlackjackHand hand(Bet bet, String... codes) {
        BlackjackHand hand = new BlackjackHand(UUID.randomUUID(), UUID.randomUUID(), bet,
                BlackjackRules.deal(StackedDeck.startingWith(codes)), NOW);
        ReflectionTestUtils.setField(hand, "id", UUID.randomUUID());
        return hand;
    }
}

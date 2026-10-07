package com.iulianlounge.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.iulianlounge.backend.domain.Card;
import com.iulianlounge.backend.domain.Shuffler;

class BlackjackConfigTest {

    private final Shuffler shuffler = new BlackjackConfig().shuffler();

    @Test
    void theShufflerKeepsTheFiftyTwoCards() {
        List<Card> shuffled = shuffler.shuffle(Card.deck());

        assertEquals(52, shuffled.size());
        assertEquals(new HashSet<>(Card.deck()), new HashSet<>(shuffled));
    }

    @Test
    void theShufflerReturnsANewListAndLeavesTheInputAlone() {
        List<Card> deck = Card.deck();

        List<Card> shuffled = shuffler.shuffle(deck);

        assertNotSame(deck, shuffled);
        assertEquals(Card.deck(), deck);
    }

    @Test
    void successiveShufflesDoNotAllComeOutInTheSameOrder() {
        List<Card> first = shuffler.shuffle(Card.deck());

        boolean differsFromAnotherShuffle = false;
        for (int i = 0; i < 5 && !differsFromAnotherShuffle; i++) {
            differsFromAnotherShuffle = !first.equals(shuffler.shuffle(Card.deck()));
        }

        assertTrue(differsFromAnotherShuffle);
    }
}

package com.iulianlounge.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RankTest {

    @Test
    void ranksAreDeclaredFromTheLowestToTheHighestThreshold() {
        Rank[] ranks = Rank.values();

        for (int i = 1; i < ranks.length; i++) {
            assertTrue(ranks[i].minSpent() > ranks[i - 1].minSpent(), ranks[i].name());
        }
        assertEquals(0, Rank.NADIE.minSpent());
    }

    @ParameterizedTest
    @CsvSource({
        "-5, NADIE",
        "0, NADIE",
        "24, NADIE",
        "25, HABITUAL",
        "99, HABITUAL",
        "100, CONFIANZA",
        "299, CONFIANZA",
        "300, SOCIO",
        "5000, SOCIO"
    })
    void theRankComesFromWhatWasSpentAtTheBar(long spent, Rank expected) {
        assertEquals(expected, Rank.forSpent(spent));
    }
}

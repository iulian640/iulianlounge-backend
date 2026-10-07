package com.iulianlounge.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RankTest {

    @ParameterizedTest
    @CsvSource({
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

package com.iulianlounge.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BarmanSituationTest {

    @ParameterizedTest
    @CsvSource({
        "GREETING, NADIE, barman.greeting.nadie",
        "SERVE, HABITUAL, barman.serve.habitual",
        "PROMOTION, CONFIANZA, barman.promotion.confianza",
        "BROKE, SOCIO, barman.broke.socio",
        "NO_CREDIT, HABITUAL, barman.no_credit",
        "HOUSE_CREDIT, SOCIO, barman.house_credit",
        "BUSY, NADIE, barman.busy",
        "BUSY, SOCIO, barman.busy"
    })
    void theLineKeyNamesTheSituationAndTheRankWhenItDependsOnIt(BarmanSituation situation, Rank rank,
            String key) {
        assertEquals(key, situation.lineFor(rank));
    }
}

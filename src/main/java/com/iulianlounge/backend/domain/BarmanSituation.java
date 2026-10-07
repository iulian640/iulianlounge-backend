package com.iulianlounge.backend.domain;

import java.util.Locale;

public enum BarmanSituation {
    GREETING(true),
    SERVE(true),
    PROMOTION(true),
    BROKE(true),
    NO_CREDIT(false),
    HOUSE_CREDIT(false);

    private final boolean byRank;

    BarmanSituation(boolean byRank) {
        this.byRank = byRank;
    }

    public String lineFor(Rank rank) {
        String situation = "barman." + name().toLowerCase(Locale.ROOT);
        return byRank ? situation + "." + rank.name().toLowerCase(Locale.ROOT) : situation;
    }
}

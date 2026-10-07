package com.iulianlounge.backend.domain;

public enum Rank {
    NADIE(0),
    HABITUAL(25),
    CONFIANZA(100),
    SOCIO(300);

    private final long minSpent;

    Rank(long minSpent) {
        this.minSpent = minSpent;
    }

    public long minSpent() {
        return minSpent;
    }

    public static Rank forSpent(long spent) {
        Rank rank = NADIE;
        for (Rank candidate : values()) {
            if (spent >= candidate.minSpent) {
                rank = candidate;
            }
        }
        return rank;
    }
}

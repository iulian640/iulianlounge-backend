package com.iulianlounge.backend.domain;

import java.util.Arrays;

public enum Drink {
    BATHTUB_GIN(5),
    BEES_KNEES(10),
    GIN_RICKEY(15),
    SIDECAR(25),
    FRENCH_75(40);

    private final long price;

    Drink(long price) {
        this.price = price;
    }

    public long price() {
        return price;
    }

    public static long cheapestPrice() {
        return Arrays.stream(values()).mapToLong(Drink::price).min().orElseThrow();
    }
}

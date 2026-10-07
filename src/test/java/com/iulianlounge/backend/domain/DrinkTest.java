package com.iulianlounge.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class DrinkTest {

    @Test
    void everyDrinkHasItsOwnPositivePrice() {
        long distinctPrices = Arrays.stream(Drink.values()).mapToLong(Drink::price).distinct().count();

        assertEquals(Drink.values().length, distinctPrices);
        assertTrue(Arrays.stream(Drink.values()).allMatch(drink -> drink.price() > 0));
    }

    @Test
    void theCheapestDrinkIsTheBathtubGin() {
        assertEquals(Drink.BATHTUB_GIN.price(), Drink.cheapestPrice());
        assertEquals(5, Drink.cheapestPrice());
    }
}

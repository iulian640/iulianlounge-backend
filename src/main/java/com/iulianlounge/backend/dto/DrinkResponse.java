package com.iulianlounge.backend.dto;

import java.util.Arrays;
import java.util.List;

import com.iulianlounge.backend.domain.Drink;

public record DrinkResponse(Drink code, long price) {

    public static List<DrinkResponse> menu() {
        return Arrays.stream(Drink.values()).map(drink -> new DrinkResponse(drink, drink.price())).toList();
    }
}

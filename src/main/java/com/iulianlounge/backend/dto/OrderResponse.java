package com.iulianlounge.backend.dto;

import com.iulianlounge.backend.domain.Drink;
import com.iulianlounge.backend.domain.Rank;

public record OrderResponse(Drink drink, long price, long balance, Rank rank, boolean promoted,
        boolean creditAvailable, String line) {
}

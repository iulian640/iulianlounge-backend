package com.iulianlounge.backend.dto;

import java.util.List;

import com.iulianlounge.backend.domain.Rank;

public record BarResponse(List<DrinkResponse> drinks, long balance, Rank rank, boolean creditAvailable,
        String line) {
}

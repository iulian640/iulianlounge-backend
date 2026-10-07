package com.iulianlounge.backend.dto;

import java.util.List;

import com.iulianlounge.backend.domain.Rank;

public record TalkFacts(List<DrinkResponse> drinks, long balance, Rank rank, long spent, boolean creditAvailable) {
}

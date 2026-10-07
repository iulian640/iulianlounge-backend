package com.iulianlounge.backend.dto;

import com.iulianlounge.backend.domain.Rank;

public record HouseCreditResponse(long amount, long balance, Rank rank, String line) {
}

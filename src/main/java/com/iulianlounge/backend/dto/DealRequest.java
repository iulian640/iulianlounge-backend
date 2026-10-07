package com.iulianlounge.backend.dto;

import jakarta.validation.constraints.NotNull;

import com.iulianlounge.backend.domain.Bet;

public record DealRequest(@NotNull Bet bet) {
}

package com.iulianlounge.backend.dto;

import java.util.Arrays;
import java.util.List;

import com.iulianlounge.backend.domain.Bet;

public record BetOption(Bet code, long chips) {

    public static List<BetOption> all() {
        return Arrays.stream(Bet.values()).map(bet -> new BetOption(bet, bet.chips())).toList();
    }
}

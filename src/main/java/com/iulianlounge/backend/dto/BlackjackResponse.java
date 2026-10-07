package com.iulianlounge.backend.dto;

import java.util.List;

public record BlackjackResponse(HandView hand, long balance, List<BetOption> bets) {
}

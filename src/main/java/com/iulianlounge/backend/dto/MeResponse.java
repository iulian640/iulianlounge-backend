package com.iulianlounge.backend.dto;

import java.util.UUID;

import com.iulianlounge.backend.domain.Rank;

public record MeResponse(UUID userId, String username, String locale, Rank rank) {
}

package com.iulianlounge.backend.security;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenClaims(UUID userId, Instant expiresAt) {
}

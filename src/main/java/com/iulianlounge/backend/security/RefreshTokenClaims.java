package com.iulianlounge.backend.security;

import java.time.Instant;
import java.util.UUID;

// expiresAt viaja a la rotación: el refresh nuevo caduca cuando el del login, no 7 días después
public record RefreshTokenClaims(UUID userId, Instant expiresAt) {
}

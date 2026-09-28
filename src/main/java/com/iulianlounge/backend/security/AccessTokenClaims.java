package com.iulianlounge.backend.security;

import java.util.UUID;

public record AccessTokenClaims(UUID userId, String role) {
}

package com.iulianlounge.backend.security;

import java.util.UUID;

// Lo que el filtro JWT necesita saber de un access token válido
public record AccessTokenClaims(UUID userId, String username, String role) {
}

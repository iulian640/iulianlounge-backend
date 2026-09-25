package com.iulianlounge.backend.security;

import java.util.UUID;

// Lo que el filtro JWT necesita saber de un access token válido. Sin username: el payload lo lee cualquiera
// y quien necesite el nombre (p. ej. /me) lo saca de la BD con el userId
public record AccessTokenClaims(UUID userId, String role) {
}

package com.iulianlounge.backend.security;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

// ADR-08: el refresh vive en una cookie que el JavaScript de la página no puede leer
public final class RefreshCookies {

    public static final String NAME = "refresh_token";
    // Solo viaja a /auth: el resto de la API se autentica con la cabecera Authorization
    static final String PATH = "/api/v1/auth";

    private RefreshCookies() {
    }

    public static ResponseCookie create(String refreshToken, Duration ttl) {
        // Max-Age negativo sería una cookie de sesión sin caducidad propia: nunca
        Duration maxAge = ttl.isNegative() ? Duration.ZERO : ttl;
        return base(refreshToken).maxAge(maxAge).build();
    }

    // Mismo nombre y path que la original; si no, el navegador la trata como otra cookie y no borra nada
    public static ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                // Chrome y Firefox tratan http://localhost como seguro: Secure vale también en dev.
                // Safari no: en dev con Safari el login no persiste. Probar en Chrome/Firefox
                .secure(true)
                .sameSite("Strict")
                .path(PATH);
    }
}

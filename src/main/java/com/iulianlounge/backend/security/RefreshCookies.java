package com.iulianlounge.backend.security;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

public final class RefreshCookies {

    public static final String NAME = "refresh_token";
    static final String PATH = "/api/v1/auth";

    private RefreshCookies() {
    }

    public static ResponseCookie create(String refreshToken, Duration ttl) {
        Duration maxAge = ttl.isNegative() ? Duration.ZERO : ttl;
        return base(refreshToken).maxAge(maxAge).build();
    }

    public static ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH);
    }
}

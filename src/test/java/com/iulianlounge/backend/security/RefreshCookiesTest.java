package com.iulianlounge.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class RefreshCookiesTest {

    @Test
    void createSetsEveryAttributeFromAdr08() {
        ResponseCookie cookie = RefreshCookies.create("refresh-token", Duration.ofDays(7));

        assertEquals("refresh_token", cookie.getName());
        assertEquals("refresh-token", cookie.getValue());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("Strict", cookie.getSameSite());
        assertEquals("/api/v1/auth", cookie.getPath());
        assertEquals(Duration.ofDays(7), cookie.getMaxAge());
    }

    @Test
    void createNeverProducesANegativeMaxAge() {
        // Max-Age negativo = cookie de sesión que vive hasta cerrar el navegador: justo lo contrario
        ResponseCookie cookie = RefreshCookies.create("refresh-token", Duration.ofSeconds(-5));

        assertEquals(Duration.ZERO, cookie.getMaxAge());
    }

    @Test
    void clearEmptiesTheCookieWithTheSameNameAndPath() {
        ResponseCookie cookie = RefreshCookies.clear();

        assertEquals("refresh_token", cookie.getName());
        assertEquals("", cookie.getValue());
        assertEquals("/api/v1/auth", cookie.getPath());
        assertEquals(Duration.ZERO, cookie.getMaxAge());
        // Mismos atributos que la original: Chrome no deja que una cookie sin Secure pise a una Secure
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("Strict", cookie.getSameSite());
    }
}

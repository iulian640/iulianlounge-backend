package com.iulianlounge.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.exception.InvalidTokenException;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

class JwtServiceTest {

    private static final String SECRET = "test-secret-que-tiene-mas-de-32-bytes!!";
    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("cursaito");
        user.setRole("USER");
    }

    @Test
    void accessTokenCarriesUserIdUsernameAndRole() {
        String token = jwtService.generateAccessToken(user);

        AccessTokenClaims claims = jwtService.validateAccessToken(token);

        assertEquals(user.getId(), claims.userId());
        assertEquals("cursaito", claims.username());
        assertEquals("USER", claims.role());
    }

    @Test
    void refreshTokenCarriesUserId() {
        String token = jwtService.generateRefreshToken(user);

        assertEquals(user.getId(), jwtService.validateRefreshToken(token));
    }

    @Test
    void accessTokenIsRejectedAfter15Minutes() {
        String token = jwtService.generateAccessToken(user);
        JwtService sixteenMinutesLater =
                new JwtService(SECRET, Clock.fixed(NOW.plus(JwtService.ACCESS_TTL).plusSeconds(60), ZoneOffset.UTC));

        assertThrows(InvalidTokenException.class, () -> sixteenMinutesLater.validateAccessToken(token));
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccessToken() {
        String refresh = jwtService.generateRefreshToken(user);

        assertThrows(InvalidTokenException.class, () -> jwtService.validateAccessToken(refresh));
    }

    @Test
    void accessTokenIsNotAcceptedAsRefreshToken() {
        String access = jwtService.generateAccessToken(user);

        assertThrows(InvalidTokenException.class, () -> jwtService.validateRefreshToken(access));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService otherKey = new JwtService("otro-secreto-distinto-de-mas-de-32-bytes", Clock.fixed(NOW, ZoneOffset.UTC));
        String forged = otherKey.generateAccessToken(user);

        assertThrows(InvalidTokenException.class, () -> jwtService.validateAccessToken(forged));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateAccessToken(user);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThrows(InvalidTokenException.class, () -> jwtService.validateAccessToken(tampered));
    }

    @Test
    void garbageTokenIsRejected() {
        assertThrows(InvalidTokenException.class, () -> jwtService.validateAccessToken("no-soy-un-jwt"));
    }

    @Test
    void tokensAreSignedWithHs256EvenWithALongSecret() {
        // Un secreto de 64 bytes haría que jjwt eligiera HS512 si no lo fijáramos
        String longSecret = "x".repeat(64);
        JwtService service = new JwtService(longSecret, Clock.fixed(NOW, ZoneOffset.UTC));
        SecretKey key = Keys.hmacShaKeyFor(longSecret.getBytes(StandardCharsets.UTF_8));

        for (String token : new String[] {service.generateAccessToken(user), service.generateRefreshToken(user)}) {
            String alg = Jwts.parser().verifyWith(key)
                    .clock(() -> Date.from(NOW)).build()
                    .parseSignedClaims(token).getHeader().getAlgorithm();
            assertEquals("HS256", alg);
        }
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThrows(WeakKeyException.class, () -> new JwtService("corto", Clock.systemUTC()));
    }
}

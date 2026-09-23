package com.iulianlounge.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.exception.InvalidTokenException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// ADR-08: access 15 min (sub, username, role) y refresh 7 días (type=refresh), HMAC-SHA256, sin BD
@Service
public class JwtService {

    public static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    public static final Duration REFRESH_TTL = Duration.ofDays(7);

    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    private final SecretKey key;
    private final Clock clock;

    @Autowired
    public JwtService(@Value("${jwt.secret}") String secret) {
        this(secret, Clock.systemUTC());
    }

    // Los tests inyectan un reloj fijo para probar la caducidad sin esperar 15 minutos
    JwtService(String secret, Clock clock) {
        // Lanza WeakKeyException si el secreto tiene menos de 32 bytes: la app no arranca con una clave débil
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String generateAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .claim(TYPE_CLAIM, ACCESS_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TTL)))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(TYPE_CLAIM, REFRESH_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(REFRESH_TTL)))
                .signWith(key)
                .compact();
    }

    public AccessTokenClaims validateAccessToken(String token) {
        Claims claims = parse(token, ACCESS_TYPE);
        return new AccessTokenClaims(
                UUID.fromString(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class));
    }

    public UUID validateRefreshToken(String token) {
        return UUID.fromString(parse(token, REFRESH_TYPE).getSubject());
    }

    // Firma, caducidad y tipo: un refresh no vale como access ni al revés
    private Claims parse(String token, String expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Token inválido o caducado");
        }
        if (!expectedType.equals(claims.get(TYPE_CLAIM, String.class))) {
            throw new InvalidTokenException("Tipo de token incorrecto");
        }
        return claims;
    }
}

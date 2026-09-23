package com.iulianlounge.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.Supplier;

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
    // Si JWT_SECRET se reutilizara en otro entorno o servicio, sus tokens no valdrían aquí
    static final String ISSUER = "iulianlounge";
    static final String AUDIENCE = "iulianlounge-api";
    // Un único mensaje para todo token rechazado: no revela si la firma era buena
    private static final String INVALID_TOKEN = "Token inválido o caducado";

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
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .claim(TYPE_CLAIM, ACCESS_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TTL)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(user.getId().toString())
                .claim(TYPE_CLAIM, REFRESH_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(REFRESH_TTL)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public AccessTokenClaims validateAccessToken(String token) {
        Claims claims = parse(token, ACCESS_TYPE);
        return readOrReject(() -> new AccessTokenClaims(
                UUID.fromString(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class)));
    }

    public UUID validateRefreshToken(String token) {
        Claims claims = parse(token, REFRESH_TYPE);
        return readOrReject(() -> UUID.fromString(claims.getSubject()));
    }

    // Firma buena pero claims rotos (sub ausente o no-UUID): 401, no 500
    private static <T> T readOrReject(Supplier<T> read) {
        try {
            return read.get();
        } catch (IllegalArgumentException | NullPointerException | JwtException ex) {
            throw new InvalidTokenException(INVALID_TOKEN);
        }
    }

    // Firma, caducidad y tipo: un refresh no vale como access ni al revés
    private Claims parse(String token, String expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .requireAudience(AUDIENCE)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException(INVALID_TOKEN);
        }
        if (!expectedType.equals(claims.get(TYPE_CLAIM, String.class))) {
            throw new InvalidTokenException(INVALID_TOKEN);
        }
        return claims;
    }
}

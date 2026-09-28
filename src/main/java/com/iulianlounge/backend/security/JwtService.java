package com.iulianlounge.backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.iulianlounge.backend.domain.Role;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.exception.InvalidTokenException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    public static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    public static final Duration REFRESH_TTL = Duration.ofDays(7);

    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";
    static final String ISSUER = "iulianlounge";
    static final String AUDIENCE = "iulianlounge-api";
    private static final Set<String> ALLOWED_ROLES =
            Arrays.stream(Role.values()).map(Role::name).collect(Collectors.toUnmodifiableSet());

    private final SecretKey key;
    private final Clock clock;

    public JwtService(@Value("${jwt.secret}") String secret, Clock clock) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.clock = clock;
    }

    public String generateAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim(TYPE_CLAIM, ACCESS_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TTL)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(User user) {
        return generateRefreshToken(user, clock.instant().plus(REFRESH_TTL));
    }

    public String generateRefreshToken(User user, Instant expiresAt) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(user.getId().toString())
                .claim(TYPE_CLAIM, REFRESH_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public AccessTokenClaims validateAccessToken(String token) {
        Claims claims = parse(token, ACCESS_TYPE);
        return readOrReject(() -> {
            String role = claims.get("role", String.class);
            if (!ALLOWED_ROLES.contains(role)) {
                throw new IllegalArgumentException("Rol desconocido");
            }
            return new AccessTokenClaims(
                    UUID.fromString(claims.getSubject()),
                    role);
        });
    }

    public RefreshTokenClaims validateRefreshToken(String token) {
        Claims claims = parse(token, REFRESH_TYPE);
        return readOrReject(() -> new RefreshTokenClaims(
                UUID.fromString(claims.getSubject()),
                claims.getExpiration().toInstant()));
    }

    private static <T> T readOrReject(Supplier<T> read) {
        try {
            return read.get();
        } catch (IllegalArgumentException | NullPointerException | JwtException ex) {
            throw new InvalidTokenException();
        }
    }

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
            throw new InvalidTokenException();
        }
        if (!expectedType.equals(claims.get(TYPE_CLAIM, String.class))) {
            throw new InvalidTokenException();
        }
        return claims;
    }
}

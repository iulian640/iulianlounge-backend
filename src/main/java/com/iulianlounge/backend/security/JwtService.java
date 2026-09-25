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

// ADR-08: access 15 min (sub, role) y refresh 7 días (type=refresh), HMAC-SHA256, sin BD
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
    // Roles que el filtro puede convertir en ROLE_*: los del enum; cualquier otro (o ninguno) invalida el token
    private static final Set<String> ALLOWED_ROLES =
            Arrays.stream(Role.values()).map(Role::name).collect(Collectors.toUnmodifiableSet());

    private final SecretKey key;
    private final Clock clock;

    // Mismo bean Clock que AuthService: la cookie y el exp del token salen del mismo reloj.
    // Los tests pasan uno fijo para probar la caducidad sin esperar 15 minutos
    public JwtService(@Value("${jwt.secret}") String secret, Clock clock) {
        // JWT_SECRET en base64 (openssl rand -base64 32): así la clave son bytes aleatorios de verdad, no texto tecleable.
        // No arranca si no es base64 (DecodingException) o si decodifica a menos de 32 bytes (WeakKeyException)
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

    // Rotación: el refresh nuevo hereda la caducidad del anterior, así la sesión muere a los 7 días del login
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

    // Firma buena pero claims rotos (sub ausente o no-UUID): 401, no 500
    private static <T> T readOrReject(Supplier<T> read) {
        try {
            return read.get();
        } catch (IllegalArgumentException | NullPointerException | JwtException ex) {
            throw new InvalidTokenException();
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
            throw new InvalidTokenException();
        }
        if (!expectedType.equals(claims.get(TYPE_CLAIM, String.class))) {
            throw new InvalidTokenException();
        }
        return claims;
    }
}

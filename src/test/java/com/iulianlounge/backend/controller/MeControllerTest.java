package com.iulianlounge.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.iulianlounge.backend.config.ClockConfig;
import com.iulianlounge.backend.config.SecurityConfig;
import com.iulianlounge.backend.domain.Rank;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.MeResponse;
import com.iulianlounge.backend.exception.InvalidTokenException;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.service.UserService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// Seguridad REAL (SecurityConfig + filtro + JwtService): aquí se prueba que la cadena protege la ruta
@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, JwtService.class, ClockConfig.class})
@TestPropertySource(properties = "jwt.secret=" + MeControllerTest.SECRET)
class MeControllerTest {

    static final String SECRET = "test-secret-que-tiene-mas-de-32-bytes!!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("cursaito");
        user.setRole("USER");
    }

    @Test
    void meWithValidAccessTokenReturnsProfile() throws Exception {
        when(userService.getProfile(user.getId()))
                .thenReturn(new MeResponse(user.getId(), "cursaito", "es", Rank.NADIE));

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.username").value("cursaito"))
                .andExpect(jsonPath("$.locale").value("es"))
                .andExpect(jsonPath("$.rank").value("NADIE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "https://evil.example"})
    void noOriginGetsCorsPermission(String origin) throws Exception {
        // ADR-08: sin CORS (mismo origen en prod, proxy de Vite en dev). Ningún origen ajeno,
        // ni el de dev, recibe permiso: el navegador bloquea la respuesta y nadie lee el access de /refresh
        mockMvc.perform(options("/api/v1/auth/refresh")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void meForADeletedAccountReturns401() throws Exception {
        when(userService.getProfile(user.getId()))
                .thenThrow(new InvalidTokenException("Token inválido o caducado"));

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(user)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithExpiredAccessTokenReturns401() throws Exception {
        // Firmado con la clave buena, iss/aud correctos, pero caducado hace un minuto
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .issuer("iulianlounge")
                .audience().add("iulianlounge-api").and()
                .subject(user.getId().toString())
                .claim("username", "cursaito")
                .claim("role", "USER")
                .claim("type", "access")
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRouteWithValidTokenIs404Not401() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe")
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(user)))
                .andExpect(status().isNotFound());
    }

    @Test
    void meWithoutTokenReturns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Autenticación requerida"));
    }

    @Test
    void meWithTamperedTokenReturns401() throws Exception {
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + token.substring(0, token.length() - 2) + "xx"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithRefreshTokenInsteadOfAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + jwtService.generateRefreshToken(user)))
                .andExpect(status().isUnauthorized());
    }
}

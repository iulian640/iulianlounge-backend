package com.iulianlounge.backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.iulianlounge.backend.config.SecurityConfig;
import com.iulianlounge.backend.domain.Rank;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.MeResponse;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.service.UserService;

// Seguridad REAL (SecurityConfig + filtro + JwtService): aquí se prueba que la cadena protege la ruta
@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = "jwt.secret=test-secret-que-tiene-mas-de-32-bytes!!")
class MeControllerTest {

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

    @Test
    void preflightFromTheDevFrontendIsAllowed() throws Exception {
        // El preflight no lleva token: si la seguridad lo tratara como anónimo, el navegador bloquearía /me
        mockMvc.perform(options("/api/v1/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void preflightFromAnUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/me")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
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

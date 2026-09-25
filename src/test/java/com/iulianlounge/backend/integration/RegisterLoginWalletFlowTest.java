package com.iulianlounge.backend.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

// La app entera contra el Postgres real: controller → service → repository → BD, con seguridad y Flyway.
// @Transactional: todo lo que crea el test se deshace al acabar
@SpringBootTest(properties = "jwt.secret=dGVzdC1zZWNyZXQtcXVlLXRpZW5lLW1hcy1kZS0zMi1ieXRlcyEh")
@AutoConfigureMockMvc
@Transactional
class RegisterLoginWalletFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aNewPlayerRegistersLogsInAndFindsTheWelcomeChips() throws Exception {
        // Nombre único: la BD de desarrollo puede tener ya otros usuarios
        String username = "flujo_" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@lounge.com","password":"12345678","locale":"es"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated());

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"12345678"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.read(loginBody, "$.accessToken");

        mockMvc.perform(get("/api/v1/wallet").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100));

        mockMvc.perform(get("/api/v1/wallet/transactions").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("WELCOME_BONUS"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(100));
    }
}

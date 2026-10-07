package com.iulianlounge.backend.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQtcXVlLXRpZW5lLW1hcy1kZS0zMi1ieXRlcyEh",
        "auth.rate-limit.max-per-minute=1000"
})
@AutoConfigureMockMvc
class BarFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aNewMemberDrinksClimbsTheRanksRunsDryAndTheHouseLendsOncePerDay() throws Exception {
        String bearer = registerAndLogIn();

        mockMvc.perform(get("/api/v1/bar").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100))
                .andExpect(jsonPath("$.rank").value("NADIE"))
                .andExpect(jsonPath("$.line").value("barman.greeting.nadie"));

        String firstOrder = UUID.randomUUID().toString();
        order(bearer, firstOrder, "FRENCH_75")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(60))
                .andExpect(jsonPath("$.rank").value("HABITUAL"))
                .andExpect(jsonPath("$.promoted").value(true))
                .andExpect(jsonPath("$.line").value("barman.promotion.habitual"));
        order(bearer, firstOrder, "FRENCH_75")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(60))
                .andExpect(jsonPath("$.promoted").value(true))
                .andExpect(jsonPath("$.line").value("barman.promotion.habitual"));

        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value("HABITUAL"));

        order(bearer, UUID.randomUUID().toString(), "SIDECAR").andExpect(jsonPath("$.balance").value(35));
        order(bearer, UUID.randomUUID().toString(), "GIN_RICKEY").andExpect(jsonPath("$.balance").value(20));
        order(bearer, UUID.randomUUID().toString(), "BEES_KNEES").andExpect(jsonPath("$.balance").value(10));
        order(bearer, UUID.randomUUID().toString(), "BATHTUB_GIN").andExpect(jsonPath("$.balance").value(5));
        order(bearer, UUID.randomUUID().toString(), "BATHTUB_GIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.rank").value("CONFIANZA"))
                .andExpect(jsonPath("$.creditAvailable").value(true))
                .andExpect(jsonPath("$.line").value("barman.promotion.confianza"));

        order(bearer, UUID.randomUUID().toString(), "FRENCH_75")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("wallet.insufficient_funds"));

        houseCredit(bearer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(50))
                .andExpect(jsonPath("$.balance").value(50))
                .andExpect(jsonPath("$.line").value("barman.house_credit"));
        houseCredit(bearer)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("bar.credit_not_needed"));

        order(bearer, UUID.randomUUID().toString(), "FRENCH_75").andExpect(jsonPath("$.balance").value(10));
        order(bearer, UUID.randomUUID().toString(), "BEES_KNEES")
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.creditAvailable").value(false))
                .andExpect(jsonPath("$.line").value("barman.no_credit"));
        houseCredit(bearer)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("bar.credit_used_today"));
    }

    private String registerAndLogIn() throws Exception {
        String username = "barra_" + UUID.randomUUID().toString().substring(0, 8);
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
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(loginBody, "$.accessToken");
    }

    private ResultActions order(String bearer, String idempotencyKey, String drink) throws Exception {
        return mockMvc.perform(post("/api/v1/bar/orders")
                .header("Authorization", bearer)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"drink":"%s"}
                        """.formatted(drink)));
    }

    private ResultActions houseCredit(String bearer) throws Exception {
        return mockMvc.perform(post("/api/v1/bar/house-credit").header("Authorization", bearer));
    }
}

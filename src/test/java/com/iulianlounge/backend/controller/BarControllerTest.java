package com.iulianlounge.backend.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import com.iulianlounge.backend.config.ClockConfig;
import com.iulianlounge.backend.config.SecurityConfig;
import com.iulianlounge.backend.domain.Drink;
import com.iulianlounge.backend.domain.Rank;
import com.iulianlounge.backend.domain.Role;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.BarResponse;
import com.iulianlounge.backend.dto.DrinkResponse;
import com.iulianlounge.backend.dto.OrderResponse;
import com.iulianlounge.backend.exception.InsufficientFundsException;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.service.BarService;

@WebMvcTest(BarController.class)
@Import({SecurityConfig.class, JwtService.class, ClockConfig.class})
@TestPropertySource(properties = "jwt.secret=" + MeControllerTest.SECRET)
class BarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private BarService barService;

    private User user;
    private String bearer;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(Role.USER);
        bearer = "Bearer " + jwtService.generateAccessToken(user);
    }

    @Test
    void barReturnsTheMenuOfTheTokenOwner() throws Exception {
        when(barService.menu(user.getId())).thenReturn(
                new BarResponse(DrinkResponse.menu(), 60, Rank.HABITUAL, false, "barman.greeting.habitual"));

        mockMvc.perform(get("/api/v1/bar").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drinks.length()").value(Drink.values().length))
                .andExpect(jsonPath("$.drinks[0].code").value("BATHTUB_GIN"))
                .andExpect(jsonPath("$.drinks[0].price").value(5))
                .andExpect(jsonPath("$.balance").value(60))
                .andExpect(jsonPath("$.rank").value("HABITUAL"))
                .andExpect(jsonPath("$.creditAvailable").value(false))
                .andExpect(jsonPath("$.line").value("barman.greeting.habitual"));
    }

    @Test
    void barWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/bar"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.required"));
    }

    @Test
    void anOrderIsChargedWithItsIdempotencyKey() throws Exception {
        UUID key = UUID.randomUUID();
        when(barService.order(user.getId(), Drink.SIDECAR, key)).thenReturn(new OrderResponse(
                Drink.SIDECAR, 25, 75, Rank.HABITUAL, true, false, "barman.promotion.habitual"));

        mockMvc.perform(order(key.toString(), """
                        {"drink":"SIDECAR"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drink").value("SIDECAR"))
                .andExpect(jsonPath("$.price").value(25))
                .andExpect(jsonPath("$.balance").value(75))
                .andExpect(jsonPath("$.rank").value("HABITUAL"))
                .andExpect(jsonPath("$.promoted").value(true))
                .andExpect(jsonPath("$.line").value("barman.promotion.habitual"));
    }

    @Test
    void anOrderWithoutIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/bar/orders")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"drink":"SIDECAR"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.rejected"));
        verifyNoInteractions(barService);
    }

    @Test
    void anOrderWithAMalformedIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(order("not-a-uuid", """
                        {"drink":"SIDECAR"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.rejected"));
        verifyNoInteractions(barService);
    }

    @Test
    void anOrderOfADrinkOffTheMenuIsRejected() throws Exception {
        mockMvc.perform(order(UUID.randomUUID().toString(), """
                        {"drink":"ABSENTA"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.rejected"));
        verifyNoInteractions(barService);
    }

    @Test
    void anOrderWithoutDrinkFailsValidation() throws Exception {
        mockMvc.perform(order(UUID.randomUUID().toString(), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
        verifyNoInteractions(barService);
    }

    @Test
    void anOrderWithoutEnoughChipsIs422() throws Exception {
        UUID key = UUID.randomUUID();
        when(barService.order(user.getId(), Drink.FRENCH_75, key)).thenThrow(new InsufficientFundsException());

        mockMvc.perform(order(key.toString(), """
                        {"drink":"FRENCH_75"}
                        """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("wallet.insufficient_funds"));
    }

    private RequestBuilder order(String idempotencyKey, String body) {
        return post("/api/v1/bar/orders")
                .header("Authorization", bearer)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}

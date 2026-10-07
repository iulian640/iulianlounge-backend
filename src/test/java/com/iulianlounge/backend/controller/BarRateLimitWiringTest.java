package com.iulianlounge.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.service.BarService;

@WebMvcTest(controllers = BarController.class, properties = "bar.rate-limit.max-per-minute=2")
@Import({SecurityConfig.class, JwtService.class, ClockConfig.class})
@TestPropertySource(properties = "jwt.secret=" + MeControllerTest.SECRET)
class BarRateLimitWiringTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private BarService barService;

    @Test
    void theThirdBarRequestOfAMemberWithinAMinuteGets429WhileTheMenuStaysOpen() throws Exception {
        User member = new User();
        member.setId(UUID.randomUUID());
        member.setRole(Role.USER);
        String bearer = "Bearer " + jwtService.generateAccessToken(member);
        when(barService.order(eq(member.getId()), eq(Drink.BATHTUB_GIN), any())).thenReturn(
                new OrderResponse(Drink.BATHTUB_GIN, 5, 95, Rank.NADIE, false, false, "barman.serve.nadie"));
        when(barService.menu(member.getId())).thenReturn(
                new BarResponse(DrinkResponse.menu(), 90, Rank.NADIE, false, "barman.greeting.nadie"));

        mockMvc.perform(order(bearer)).andExpect(status().isOk());
        mockMvc.perform(order(bearer)).andExpect(status().isOk());

        mockMvc.perform(order(bearer))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("bar.too_many_requests"));
        mockMvc.perform(get("/api/v1/bar").header("Authorization", bearer)).andExpect(status().isOk());
    }

    private static RequestBuilder order(String bearer) {
        return post("/api/v1/bar/orders")
                .header("Authorization", bearer)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"drink\":\"BATHTUB_GIN\"}");
    }
}

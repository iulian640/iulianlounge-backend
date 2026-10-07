package com.iulianlounge.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.iulianlounge.backend.dto.OrderResponse;
import com.iulianlounge.backend.dto.TalkResponse;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.service.BarService;
import com.iulianlounge.backend.service.TalkService;

@WebMvcTest(controllers = {BarController.class, BarTalkController.class},
        properties = {"barman.talk.max-per-minute=3", "bar.rate-limit.max-per-minute=2"})
@Import({SecurityConfig.class, JwtService.class, ClockConfig.class})
@TestPropertySource(properties = "jwt.secret=" + MeControllerTest.SECRET)
class BarTalkRateLimitWiringTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private BarService barService;

    @MockitoBean
    private TalkService talkService;

    private String bearer;

    @BeforeEach
    void setUp() {
        User member = new User();
        member.setId(UUID.randomUUID());
        member.setRole(Role.USER);
        bearer = "Bearer " + jwtService.generateAccessToken(member);
        when(talkService.talk(eq(member.getId()), any(), any())).thenReturn(TalkResponse.fallback("barman.busy"));
        when(barService.order(eq(member.getId()), eq(Drink.BATHTUB_GIN), any())).thenReturn(
                new OrderResponse(Drink.BATHTUB_GIN, 5, 95, Rank.NADIE, false, false, "barman.serve.nadie"));
    }

    @Test
    void theFourthTalkOfAMemberWithinAMinuteGets429AndOrderingStaysOpen() throws Exception {
        mockMvc.perform(talk()).andExpect(status().isOk());
        mockMvc.perform(talk()).andExpect(status().isOk());
        mockMvc.perform(talk()).andExpect(status().isOk());

        mockMvc.perform(talk())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("bar.too_many_requests"));
        mockMvc.perform(order()).andExpect(status().isOk());
        mockMvc.perform(order()).andExpect(status().isOk());
    }

    @Test
    void talkingNeverSpendsTheOrderingBudget() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(talk());
        }

        mockMvc.perform(order()).andExpect(status().isOk());
        mockMvc.perform(order()).andExpect(status().isOk());
        mockMvc.perform(order())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("bar.too_many_requests"));
    }

    @Test
    void anExhaustedOrderingBudgetStillLetsTheMemberTalk() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(order());
        }

        mockMvc.perform(order()).andExpect(status().isTooManyRequests());
        mockMvc.perform(talk()).andExpect(status().isOk());
    }

    @Test
    void anotherMemberHasHisOwnTalkBudget() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(talk());
        }
        User other = new User();
        other.setId(UUID.randomUUID());
        other.setRole(Role.USER);
        when(talkService.talk(eq(other.getId()), any(), any())).thenReturn(TalkResponse.fallback("barman.busy"));

        mockMvc.perform(post("/api/v1/bar/talk")
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hola\"}"))
                .andExpect(status().isOk());
    }

    private RequestBuilder talk() {
        return post("/api/v1/bar/talk")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"hola\"}");
    }

    private RequestBuilder order() {
        return post("/api/v1/bar/orders")
                .header("Authorization", bearer)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"drink\":\"BATHTUB_GIN\"}");
    }
}

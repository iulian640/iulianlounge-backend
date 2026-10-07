package com.iulianlounge.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import com.iulianlounge.backend.config.ClockConfig;
import com.iulianlounge.backend.config.SecurityConfig;
import com.iulianlounge.backend.domain.Bet;
import com.iulianlounge.backend.domain.BlackjackHand;
import com.iulianlounge.backend.domain.BlackjackRules;
import com.iulianlounge.backend.domain.Role;
import com.iulianlounge.backend.domain.StackedDeck;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.BetOption;
import com.iulianlounge.backend.dto.BlackjackResponse;
import com.iulianlounge.backend.dto.HandResponse;
import com.iulianlounge.backend.dto.HandView;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.service.BlackjackService;

@WebMvcTest(controllers = BlackjackController.class, properties = "blackjack.rate-limit.max-per-minute=2")
@Import({SecurityConfig.class, JwtService.class, ClockConfig.class})
@TestPropertySource(properties = "jwt.secret=" + MeControllerTest.SECRET)
class BlackjackRateLimitWiringTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private BlackjackService blackjackService;

    @Test
    void theThirdTablePostOfAMemberWithinAMinuteGets429WhileTheTableStaysOpen() throws Exception {
        User member = new User();
        member.setId(UUID.randomUUID());
        member.setRole(Role.USER);
        String bearer = "Bearer " + jwtService.generateAccessToken(member);
        BlackjackHand hand = new BlackjackHand(member.getId(), UUID.randomUUID(), Bet.TEN,
                BlackjackRules.deal(StackedDeck.startingWith("9S", "KD", "8H", "5C")),
                Instant.parse("2026-10-07T20:00:00Z"));
        ReflectionTestUtils.setField(hand, "id", UUID.randomUUID());
        when(blackjackService.deal(eq(member.getId()), eq(Bet.TEN), any()))
                .thenReturn(new HandResponse(HandView.of(hand), 90));
        when(blackjackService.table(member.getId()))
                .thenReturn(new BlackjackResponse(null, 90, BetOption.all()));

        mockMvc.perform(deal(bearer)).andExpect(status().isOk());
        mockMvc.perform(deal(bearer)).andExpect(status().isOk());

        mockMvc.perform(deal(bearer))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("blackjack.too_many_requests"));
        mockMvc.perform(get("/api/v1/blackjack").header("Authorization", bearer)).andExpect(status().isOk());
    }

    private static RequestBuilder deal(String bearer) {
        return post("/api/v1/blackjack/hands")
                .header("Authorization", bearer)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bet\":\"TEN\"}");
    }
}

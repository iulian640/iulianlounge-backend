package com.iulianlounge.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.iulianlounge.backend.exception.HandFinishedException;
import com.iulianlounge.backend.exception.HandInProgressException;
import com.iulianlounge.backend.exception.HandNotFoundException;
import com.iulianlounge.backend.exception.InsufficientFundsException;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.service.BlackjackService;

@WebMvcTest(BlackjackController.class)
@Import({SecurityConfig.class, JwtService.class, ClockConfig.class})
@TestPropertySource(properties = "jwt.secret=" + MeControllerTest.SECRET)
class BlackjackControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-07T20:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private BlackjackService blackjackService;

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
    void theTableOfTheTokenOwnerComesWithItsHandBalanceAndBets() throws Exception {
        UUID handId = UUID.randomUUID();
        when(blackjackService.table(user.getId())).thenReturn(
                new BlackjackResponse(HandView.of(inPlay(handId)), 80, BetOption.all()));

        mockMvc.perform(get("/api/v1/blackjack").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.id").value(handId.toString()))
                .andExpect(jsonPath("$.hand.bet").value(20))
                .andExpect(jsonPath("$.hand.status").value("PLAYER_TURN"))
                .andExpect(jsonPath("$.hand.playerTotal").value(17))
                .andExpect(jsonPath("$.hand.outcome").doesNotExist())
                .andExpect(jsonPath("$.balance").value(80))
                .andExpect(jsonPath("$.bets.length()").value(3))
                .andExpect(jsonPath("$.bets[0].code").value("TEN"))
                .andExpect(jsonPath("$.bets[0].chips").value(10))
                .andExpect(jsonPath("$.bets[2].code").value("FIFTY"));
    }

    @Test
    void theTableWithoutAHandHasANullHand() throws Exception {
        when(blackjackService.table(user.getId())).thenReturn(new BlackjackResponse(null, 100, BetOption.all()));

        mockMvc.perform(get("/api/v1/blackjack").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand").doesNotExist())
                .andExpect(jsonPath("$.balance").value(100));
    }

    @Test
    void dealPassesTheKeyAndTheBetOfTheTokenOwner() throws Exception {
        UUID key = UUID.randomUUID();
        UUID handId = UUID.randomUUID();
        when(blackjackService.deal(user.getId(), Bet.TWENTY, key)).thenReturn(
                new HandResponse(HandView.of(inPlay(handId)), 80));

        mockMvc.perform(deal(key.toString(), """
                        {"bet":"TWENTY"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.id").value(handId.toString()))
                .andExpect(jsonPath("$.balance").value(80));
    }

    @Test
    void hitPassesTheHandIdOfTheTokenOwner() throws Exception {
        UUID handId = UUID.randomUUID();
        when(blackjackService.hit(user.getId(), handId)).thenReturn(
                new HandResponse(HandView.of(inPlay(handId)), 80));

        mockMvc.perform(post("/api/v1/blackjack/hands/" + handId + "/hit").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.id").value(handId.toString()));
    }

    @Test
    void standPassesTheHandIdOfTheTokenOwner() throws Exception {
        UUID handId = UUID.randomUUID();
        when(blackjackService.stand(user.getId(), handId)).thenReturn(
                new HandResponse(HandView.of(inPlay(handId)), 80));

        mockMvc.perform(post("/api/v1/blackjack/hands/" + handId + "/stand").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.id").value(handId.toString()));
    }

    @Test
    void dealWithoutIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/blackjack/hands")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bet":"TWENTY"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.rejected"));
        verifyNoInteractions(blackjackService);
    }

    @Test
    void dealWithAMalformedKeyIsRejectedWithoutEchoingIt() throws Exception {
        mockMvc.perform(deal("<script>no-es-un-uuid</script>", """
                        {"bet":"TWENTY"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.rejected"))
                .andExpect(jsonPath("$.detail").value("Request rejected"));
        verifyNoInteractions(blackjackService);
    }

    @Test
    void dealOfABetOffTheTableIsRejected() throws Exception {
        mockMvc.perform(deal(UUID.randomUUID().toString(), """
                        {"bet":"FIFTEEN"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.rejected"));
        verifyNoInteractions(blackjackService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"10", "0", "\"1\"", "\"10\""})
    void dealOfABetNamedByItsChipsOrPositionIsRejected(String bet) throws Exception {
        mockMvc.perform(deal(UUID.randomUUID().toString(), "{\"bet\":" + bet + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.rejected"));
        verifyNoInteractions(blackjackService);
    }

    @Test
    void dealWithoutABetFailsValidation() throws Exception {
        mockMvc.perform(deal(UUID.randomUUID().toString(), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"));
        verifyNoInteractions(blackjackService);
    }

    @Test
    void dealWithAHandInProgressIs409() throws Exception {
        UUID key = UUID.randomUUID();
        when(blackjackService.deal(user.getId(), Bet.TEN, key)).thenThrow(new HandInProgressException());

        mockMvc.perform(deal(key.toString(), """
                        {"bet":"TEN"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("blackjack.hand_in_progress"));
    }

    @Test
    void dealWithoutEnoughChipsIs422() throws Exception {
        UUID key = UUID.randomUUID();
        when(blackjackService.deal(user.getId(), Bet.FIFTY, key)).thenThrow(new InsufficientFundsException());

        mockMvc.perform(deal(key.toString(), """
                        {"bet":"FIFTY"}
                        """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("wallet.insufficient_funds"));
    }

    @Test
    void hitOnAHandThatIsNotTheirsIs404() throws Exception {
        UUID handId = UUID.randomUUID();
        when(blackjackService.hit(user.getId(), handId)).thenThrow(new HandNotFoundException());

        mockMvc.perform(post("/api/v1/blackjack/hands/" + handId + "/hit").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("blackjack.hand_not_found"));
    }

    @Test
    void standOnAFinishedHandIs409() throws Exception {
        UUID handId = UUID.randomUUID();
        when(blackjackService.stand(user.getId(), handId)).thenThrow(new HandFinishedException());

        mockMvc.perform(post("/api/v1/blackjack/hands/" + handId + "/stand").header("Authorization", bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("blackjack.hand_finished"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hit", "stand"})
    void aHandIdThatIsNotAUuidIs400WithoutEchoingIt(String action) throws Exception {
        mockMvc.perform(post("/api/v1/blackjack/hands/not-a-uuid-<script>/" + action).header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.rejected"))
                .andExpect(jsonPath("$.detail").value("Request rejected"));
        verifyNoInteractions(blackjackService);
    }

    @Test
    void everyEndpointNeedsAToken() throws Exception {
        UUID handId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/blackjack"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.required"));
        mockMvc.perform(post("/api/v1/blackjack/hands")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bet\":\"TEN\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.required"));
        mockMvc.perform(post("/api/v1/blackjack/hands/" + handId + "/hit"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.required"));
        mockMvc.perform(post("/api/v1/blackjack/hands/" + handId + "/stand"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.required"));
        verifyNoInteractions(blackjackService);
    }

    @Test
    void aHandInProgressShowsOneDealerCardAndNeitherTheDeckNorTheHoleCard() throws Exception {
        UUID handId = UUID.randomUUID();
        when(blackjackService.table(user.getId())).thenReturn(
                new BlackjackResponse(HandView.of(inPlay(handId)), 80, BetOption.all()));

        mockMvc.perform(get("/api/v1/blackjack").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.dealerCards.length()").value(1))
                .andExpect(jsonPath("$.hand.dealerCards[0].face").value("KING"))
                .andExpect(jsonPath("$.hand.dealerCards[0].suit").value("DIAMONDS"))
                .andExpect(jsonPath("$.hand.dealerTotal").value(10))
                .andExpect(jsonPath("$.hand.deck").doesNotExist())
                .andExpect(jsonPath("$.hand.holeCard").doesNotExist())
                .andExpect(content().string(not(containsString("FIVE"))))
                .andExpect(content().string(not(containsString("deck"))));
    }

    @Test
    void aFinishedHandShowsEveryDealerCardTheOutcomeAndThePayout() throws Exception {
        BlackjackHand hand = new BlackjackHand(user.getId(), UUID.randomUUID(), Bet.TWENTY,
                BlackjackRules.deal(StackedDeck.startingWith("AS", "9D", "KS", "8D")), NOW);
        ReflectionTestUtils.setField(hand, "id", UUID.randomUUID());
        UUID key = UUID.randomUUID();
        when(blackjackService.deal(user.getId(), Bet.TWENTY, key)).thenReturn(
                new HandResponse(HandView.of(hand), 130));

        mockMvc.perform(deal(key.toString(), """
                        {"bet":"TWENTY"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.status").value("FINISHED"))
                .andExpect(jsonPath("$.hand.dealerCards.length()").value(2))
                .andExpect(jsonPath("$.hand.outcome").value("BLACKJACK"))
                .andExpect(jsonPath("$.hand.payout").value(50))
                .andExpect(jsonPath("$.balance").value(130))
                .andExpect(jsonPath("$.hand.deck").doesNotExist());
    }

    private BlackjackHand inPlay(UUID id) {
        BlackjackHand hand = new BlackjackHand(user.getId(), UUID.randomUUID(), Bet.TWENTY,
                BlackjackRules.deal(StackedDeck.startingWith("9S", "KD", "8H", "5C")), NOW);
        ReflectionTestUtils.setField(hand, "id", id);
        return hand;
    }

    private RequestBuilder deal(String idempotencyKey, String body) {
        return post("/api/v1/blackjack/hands")
                .header("Authorization", bearer)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}

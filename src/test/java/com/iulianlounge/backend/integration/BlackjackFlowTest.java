package com.iulianlounge.backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.iulianlounge.backend.domain.Bet;
import com.iulianlounge.backend.domain.BlackjackHand;
import com.iulianlounge.backend.domain.BlackjackRules;
import com.iulianlounge.backend.domain.Shuffler;
import com.iulianlounge.backend.domain.StackedDeck;
import com.iulianlounge.backend.domain.TransactionType;
import com.iulianlounge.backend.repository.BlackjackHandRepository;
import com.iulianlounge.backend.service.WalletService;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQtcXVlLXRpZW5lLW1hcy1kZS0zMi1ieXRlcyEh",
        "auth.rate-limit.max-per-minute=1000"
})
@AutoConfigureMockMvc
class BlackjackFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WalletService walletService;

    @Autowired
    private BlackjackHandRepository handRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Shuffler shuffler;

    @Test
    void aNewMemberBetsHitsStandsAndWinsAndTheLedgerShowsBothMovements() throws Exception {
        String bearer = registerAndLogIn();
        stack("5S", "9D", "6H", "8C", "TC");

        String body = deal(bearer, UUID.randomUUID(), "TWENTY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.status").value("PLAYER_TURN"))
                .andExpect(jsonPath("$.hand.playerTotal").value(11))
                .andExpect(jsonPath("$.hand.dealerCards.length()").value(1))
                .andExpect(jsonPath("$.hand.dealerTotal").value(9))
                .andExpect(jsonPath("$.balance").value(80))
                .andReturn().getResponse().getContentAsString();
        String handId = JsonPath.read(body, "$.hand.id");

        action(bearer, handId, "hit")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.playerTotal").value(21))
                .andExpect(jsonPath("$.hand.status").value("PLAYER_TURN"))
                .andExpect(jsonPath("$.balance").value(80));
        action(bearer, handId, "stand")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.status").value("FINISHED"))
                .andExpect(jsonPath("$.hand.outcome").value("WIN"))
                .andExpect(jsonPath("$.hand.payout").value(40))
                .andExpect(jsonPath("$.hand.dealerCards.length()").value(2))
                .andExpect(jsonPath("$.hand.dealerTotal").value(17))
                .andExpect(jsonPath("$.balance").value(120));

        mockMvc.perform(get("/api/v1/wallet/transactions").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].type").value("BLACKJACK_PAYOUT"))
                .andExpect(jsonPath("$.content[0].amount").value(40))
                .andExpect(jsonPath("$.content[1].type").value("BLACKJACK_BET"))
                .andExpect(jsonPath("$.content[1].amount").value(-20));
        table(bearer)
                .andExpect(jsonPath("$.hand").doesNotExist())
                .andExpect(jsonPath("$.balance").value(120));
    }

    @Test
    void betsNeverMoveTheRankBecauseOnlyTheBarCounts() throws Exception {
        String bearer = registerAndLogIn();
        stack("AS", "9D", "KS", "8D");

        deal(bearer, UUID.randomUUID(), "FIFTY").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value("NADIE"));
    }

    @Test
    void replayingTheDealKeyGivesTheSameHandAndASecondDealIsRefused() throws Exception {
        String bearer = registerAndLogIn();
        stack("9S", "KD", "8H", "5C");
        UUID key = UUID.randomUUID();

        String first = deal(bearer, key, "TWENTY").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String handId = JsonPath.read(first, "$.hand.id");

        deal(bearer, key, "TWENTY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.id").value(handId))
                .andExpect(jsonPath("$.balance").value(80));
        deal(bearer, key, "FIFTY")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("wallet.idempotency_mismatch"));
        deal(bearer, UUID.randomUUID(), "TEN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("blackjack.hand_in_progress"));

        table(bearer)
                .andExpect(jsonPath("$.hand.id").value(handId))
                .andExpect(jsonPath("$.balance").value(80))
                .andExpect(jsonPath("$.bets.length()").value(3));
        mockMvc.perform(get("/api/v1/wallet/transactions").header("Authorization", bearer))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void aPlayerNaturalIsPaidTwoAndAHalfTimesAtOnce() throws Exception {
        String bearer = registerAndLogIn();
        stack("AS", "9D", "KS", "8D");

        String body = deal(bearer, UUID.randomUUID(), "TWENTY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.status").value("FINISHED"))
                .andExpect(jsonPath("$.hand.outcome").value("BLACKJACK"))
                .andExpect(jsonPath("$.hand.payout").value(50))
                .andExpect(jsonPath("$.balance").value(130))
                .andReturn().getResponse().getContentAsString();
        String handId = JsonPath.read(body, "$.hand.id");

        action(bearer, handId, "hit")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("blackjack.hand_finished"));
        table(bearer).andExpect(jsonPath("$.hand").doesNotExist());
    }

    @Test
    void aDealerNaturalTakesTheBetAndPaysNothing() throws Exception {
        String bearer = registerAndLogIn();
        stack("9S", "AD", "8H", "KC");

        deal(bearer, UUID.randomUUID(), "TEN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.outcome").value("LOSE"))
                .andExpect(jsonPath("$.hand.payout").value(0))
                .andExpect(jsonPath("$.hand.dealerCards.length()").value(2))
                .andExpect(jsonPath("$.balance").value(90));
        mockMvc.perform(get("/api/v1/wallet/transactions").header("Authorization", bearer))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void aHandOfAnotherMemberIsNotFound() throws Exception {
        String owner = registerAndLogIn();
        String stranger = registerAndLogIn();
        stack("9S", "KD", "8H", "5C");
        String body = deal(owner, UUID.randomUUID(), "TEN").andReturn().getResponse().getContentAsString();
        String handId = JsonPath.read(body, "$.hand.id");

        action(stranger, handId, "hit")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("blackjack.hand_not_found"));
        action(stranger, handId, "stand")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("blackjack.hand_not_found"));
        action(owner, UUID.randomUUID().toString(), "hit")
                .andExpect(status().isNotFound());
    }

    @Test
    void aBetBeyondWhatIsLeftIsRefusedAndTheWalletIsUntouched() throws Exception {
        String bearer = registerAndLogIn();
        stack("TS", "TD", "7H", "9C");
        for (int i = 0; i < 2; i++) {
            String body = deal(bearer, UUID.randomUUID(), "FIFTY")
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            action(bearer, JsonPath.read(body, "$.hand.id"), "stand")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hand.outcome").value("LOSE"));
        }

        deal(bearer, UUID.randomUUID(), "TEN")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("wallet.insufficient_funds"));

        table(bearer)
                .andExpect(jsonPath("$.hand").doesNotExist())
                .andExpect(jsonPath("$.balance").value(0));
        mockMvc.perform(get("/api/v1/wallet/transactions").header("Authorization", bearer))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void anOrphanBetShowsUpAsTheHandInProgressOnTheNextTableRead() throws Exception {
        String bearer = registerAndLogIn();
        UUID userId = userIdOf(bearer);
        UUID key = UUID.randomUUID();
        stack("9S", "KD", "8H", "5C");
        walletService.debit(userId, 20, TransactionType.BLACKJACK_BET, "blackjack-bet:" + key);

        String body = table(bearer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.status").value("PLAYER_TURN"))
                .andExpect(jsonPath("$.hand.bet").value(20))
                .andExpect(jsonPath("$.balance").value(80))
                .andReturn().getResponse().getContentAsString();
        String handId = JsonPath.read(body, "$.hand.id");

        deal(bearer, key, "TWENTY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.id").value(handId))
                .andExpect(jsonPath("$.balance").value(80));
        mockMvc.perform(get("/api/v1/wallet/transactions").header("Authorization", bearer))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void anOrphanBetBehindAHandInProgressIsRefundedAndItsHandIsVoid() throws Exception {
        String bearer = registerAndLogIn();
        UUID userId = userIdOf(bearer);
        stack("9S", "KD", "8H", "5C");
        deal(bearer, UUID.randomUUID(), "TEN").andExpect(status().isOk());
        walletService.debit(userId, 20, TransactionType.BLACKJACK_BET, "blackjack-bet:" + UUID.randomUUID());

        table(bearer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hand.bet").value(10))
                .andExpect(jsonPath("$.balance").value(90));
        table(bearer).andExpect(jsonPath("$.balance").value(90));
        mockMvc.perform(get("/api/v1/wallet/transactions").header("Authorization", bearer))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void aFinishedWinWithoutItsPayoutIsPaidOnTheNextReadAndNeverTwice() throws Exception {
        String bearer = registerAndLogIn();
        UUID userId = userIdOf(bearer);
        UUID key = UUID.randomUUID();
        walletService.debit(userId, 20, TransactionType.BLACKJACK_BET, "blackjack-bet:" + key);
        BlackjackHand unpaid = handRepository.saveAndFlush(new BlackjackHand(userId, key, Bet.TWENTY,
                BlackjackRules.stand(BlackjackRules.deal(StackedDeck.startingWith("TS", "TD", "9H", "7C"))),
                Instant.now().truncatedTo(ChronoUnit.MICROS)));

        table(bearer)
                .andExpect(jsonPath("$.hand").doesNotExist())
                .andExpect(jsonPath("$.balance").value(120));
        table(bearer).andExpect(jsonPath("$.balance").value(120));

        jdbcTemplate.update("UPDATE blackjack_hand SET settled_at = NULL WHERE id = ?", unpaid.getId());

        table(bearer).andExpect(jsonPath("$.balance").value(120));
        mockMvc.perform(get("/api/v1/wallet/transactions").header("Authorization", bearer))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].type").value("BLACKJACK_PAYOUT"))
                .andExpect(jsonPath("$.content[0].amount").value(40));
        Integer settled = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM blackjack_hand WHERE id = ? AND settled_at IS NOT NULL", Integer.class,
                unpaid.getId());
        assertEquals(1, settled);
    }

    private void stack(String... codes) {
        when(shuffler.shuffle(any())).thenReturn(StackedDeck.startingWith(codes));
    }

    private String registerAndLogIn() throws Exception {
        String username = "mesa_" + UUID.randomUUID().toString().substring(0, 8);
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

    private UUID userIdOf(String bearer) throws Exception {
        String body = mockMvc.perform(get("/api/v1/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.userId"));
    }

    private ResultActions table(String bearer) throws Exception {
        return mockMvc.perform(get("/api/v1/blackjack").header("Authorization", bearer));
    }

    private ResultActions deal(String bearer, UUID key, String bet) throws Exception {
        return mockMvc.perform(post("/api/v1/blackjack/hands")
                .header("Authorization", bearer)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bet":"%s"}
                        """.formatted(bet)));
    }

    private ResultActions action(String bearer, String handId, String action) throws Exception {
        return mockMvc.perform(post("/api/v1/blackjack/hands/" + handId + "/" + action)
                .header("Authorization", bearer));
    }
}

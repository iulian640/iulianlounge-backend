package com.iulianlounge.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.iulianlounge.backend.exception.ErrorCode;

class MemberRateLimitFilterTest {

    private static final int LIMIT = 3;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T20:00:00Z"), ZoneOffset.UTC);
    private static final MemberRateLimitFilter.Area BAR =
            new MemberRateLimitFilter.Area("/api/v1/bar/", LIMIT, ErrorCode.BAR_TOO_MANY_REQUESTS);
    private static final MemberRateLimitFilter.Area BLACKJACK =
            new MemberRateLimitFilter.Area("/api/v1/blackjack/", LIMIT, ErrorCode.BLACKJACK_TOO_MANY_REQUESTS);
    private static final int TALK_LIMIT = 2;
    private static final MemberRateLimitFilter.Area TALK = new MemberRateLimitFilter.Area("/api/v1/bar/talk",
            MemberRateLimitFilter.Match.EXACT, TALK_LIMIT, ErrorCode.BAR_TOO_MANY_REQUESTS,
            MemberRateLimitFilter.Overflow.REJECT);
    private static final String TALK_PATH = "/api/v1/bar/talk";
    private static final String DEAL = "/api/v1/blackjack/hands";
    private static final String ORDERS = "/api/v1/bar/orders";
    private static final String HOUSE_CREDIT = "/api/v1/bar/house-credit";

    private MemberRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new MemberRateLimitFilter(List.of(BAR), CLOCK);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aMemberGetsUpToTheLimitThenA429ThatNeverReachesTheBar() throws Exception {
        UUID member = UUID.randomUUID();
        for (int i = 0; i < LIMIT; i++) {
            assertEquals(200, send("POST", ORDERS, member).getStatus());
        }
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse blocked = new MockHttpServletResponse();

        authenticate(member);
        filter.doFilter(new MockHttpServletRequest("POST", ORDERS), blocked, chain);

        assertEquals(429, blocked.getStatus());
        assertTrue(blocked.getContentType().startsWith("application/problem+json"));
        assertTrue(blocked.getContentAsString().contains("\"code\":\"bar.too_many_requests\""));
        assertEquals("60", blocked.getHeader("Retry-After"));
        assertNull(chain.getRequest());
    }

    @Test
    void eachMemberHasItsOwnBudget() throws Exception {
        UUID greedy = UUID.randomUUID();
        for (int i = 0; i <= LIMIT; i++) {
            send("POST", ORDERS, greedy);
        }

        assertEquals(200, send("POST", ORDERS, UUID.randomUUID()).getStatus());
    }

    @Test
    void ordersAndHouseCreditShareTheSameBudget() throws Exception {
        UUID member = UUID.randomUUID();
        send("POST", ORDERS, member);
        send("POST", HOUSE_CREDIT, member);
        send("POST", ORDERS, member);

        assertEquals(429, send("POST", HOUSE_CREDIT, member).getStatus());
    }

    @Test
    void readingTheMenuIsNotLimited() throws Exception {
        UUID member = UUID.randomUUID();
        for (int i = 0; i < LIMIT * 3; i++) {
            assertEquals(200, send("GET", "/api/v1/bar", member).getStatus());
        }
    }

    @Test
    void otherEndpointsAreNotLimited() throws Exception {
        UUID member = UUID.randomUUID();
        for (int i = 0; i < LIMIT * 3; i++) {
            assertEquals(200, send("POST", "/api/v1/auth/logout", member).getStatus());
        }
    }

    @Test
    void anAnonymousRequestPassesThroughSoSecurityCanAnswer401() throws Exception {
        MockFilterChain chain = null;
        for (int i = 0; i <= LIMIT; i++) {
            chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("POST", ORDERS), new MockHttpServletResponse(), chain);
        }

        assertNotNull(chain.getRequest());
    }

    @Test
    void whenTrackingIsFullNewMembersAreLetThroughInsteadOfLockingEveryoneOut() throws Exception {
        filter = new MemberRateLimitFilter(List.of(BAR), CLOCK, 2);
        UUID first = UUID.randomUUID();
        send("POST", ORDERS, first);
        send("POST", ORDERS, UUID.randomUUID());

        assertEquals(200, send("POST", ORDERS, UUID.randomUUID()).getStatus());
        send("POST", ORDERS, first);
        send("POST", ORDERS, first);
        assertEquals(429, send("POST", ORDERS, first).getStatus());
    }

    @Test
    void pastTheLimitBlackjackPostsGetA429WithTheBlackjackCode() throws Exception {
        filter = new MemberRateLimitFilter(List.of(BAR, BLACKJACK), CLOCK);
        UUID member = UUID.randomUUID();
        for (int i = 0; i < LIMIT; i++) {
            assertEquals(200, send("POST", DEAL, member).getStatus());
        }
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse blocked = new MockHttpServletResponse();

        authenticate(member);
        filter.doFilter(new MockHttpServletRequest("POST", DEAL), blocked, chain);

        assertEquals(429, blocked.getStatus());
        assertTrue(blocked.getContentType().startsWith("application/problem+json"));
        assertTrue(blocked.getContentAsString().contains("\"code\":\"blackjack.too_many_requests\""));
        assertEquals("60", blocked.getHeader("Retry-After"));
        assertNull(chain.getRequest());
    }

    @Test
    void hitAndStandShareTheBlackjackBudgetWithTheDeal() throws Exception {
        filter = new MemberRateLimitFilter(List.of(BAR, BLACKJACK), CLOCK);
        UUID member = UUID.randomUUID();
        send("POST", DEAL, member);
        send("POST", "/api/v1/blackjack/hands/" + UUID.randomUUID() + "/hit", member);
        send("POST", "/api/v1/blackjack/hands/" + UUID.randomUUID() + "/stand", member);

        assertEquals(429, send("POST", DEAL, member).getStatus());
    }

    @Test
    void theBarAndTheBlackjackTableHaveSeparateBudgetsPerMember() throws Exception {
        filter = new MemberRateLimitFilter(List.of(BAR, BLACKJACK), CLOCK);
        UUID member = UUID.randomUUID();
        for (int i = 0; i <= LIMIT; i++) {
            send("POST", DEAL, member);
        }

        assertEquals(429, send("POST", DEAL, member).getStatus());
        assertEquals(200, send("POST", ORDERS, member).getStatus());
        for (int i = 1; i < LIMIT; i++) {
            assertEquals(200, send("POST", ORDERS, member).getStatus());
        }
        assertEquals(429, send("POST", ORDERS, member).getStatus());
    }

    @Test
    void anAreaCanHaveItsOwnLimit() throws Exception {
        filter = new MemberRateLimitFilter(List.of(BAR, new MemberRateLimitFilter.Area("/api/v1/blackjack/", 1,
                ErrorCode.BLACKJACK_TOO_MANY_REQUESTS)), CLOCK);
        UUID member = UUID.randomUUID();
        send("POST", DEAL, member);

        assertEquals(429, send("POST", DEAL, member).getStatus());
        for (int i = 0; i < LIMIT; i++) {
            assertEquals(200, send("POST", ORDERS, member).getStatus());
        }
    }

    @Test
    void readingTheTableIsNotLimited() throws Exception {
        filter = new MemberRateLimitFilter(List.of(BAR, BLACKJACK), CLOCK);
        UUID member = UUID.randomUUID();
        for (int i = 0; i < LIMIT * 3; i++) {
            assertEquals(200, send("GET", "/api/v1/blackjack", member).getStatus());
        }
    }

    @Test
    void anAnonymousBlackjackRequestPassesThroughSoSecurityCanAnswer401() throws Exception {
        filter = new MemberRateLimitFilter(List.of(BAR, BLACKJACK), CLOCK);
        MockFilterChain chain = null;
        for (int i = 0; i <= LIMIT; i++) {
            chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("POST", DEAL), new MockHttpServletResponse(), chain);
        }

        assertNotNull(chain.getRequest());
    }

    @Test
    void pastItsOwnLimitTalkingGetsA429WithTheBarCodeThatNeverReachesTheTalk() throws Exception {
        filter = new MemberRateLimitFilter(List.of(BAR, TALK), CLOCK);
        UUID member = UUID.randomUUID();
        for (int i = 0; i < TALK_LIMIT; i++) {
            assertEquals(200, send("POST", TALK_PATH, member).getStatus());
        }
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse blocked = new MockHttpServletResponse();

        authenticate(member);
        filter.doFilter(new MockHttpServletRequest("POST", TALK_PATH), blocked, chain);

        assertEquals(429, blocked.getStatus());
        assertTrue(blocked.getContentType().startsWith("application/problem+json"));
        assertTrue(blocked.getContentAsString().contains("\"code\":\"bar.too_many_requests\""));
        assertEquals("60", blocked.getHeader("Retry-After"));
        assertNull(chain.getRequest());
    }

    @Test
    void talkingDoesNotSpendTheBudgetOfOrderingDrinks() throws Exception {
        filter = new MemberRateLimitFilter(List.of(BAR, TALK), CLOCK);
        UUID member = UUID.randomUUID();
        for (int i = 0; i <= TALK_LIMIT; i++) {
            send("POST", TALK_PATH, member);
        }

        for (int i = 0; i < LIMIT; i++) {
            assertEquals(200, send("POST", ORDERS, member).getStatus());
        }
        assertEquals(429, send("POST", ORDERS, member).getStatus());
    }

    @Test
    void orderingDrinksDoesNotSpendTheBudgetOfTalking() throws Exception {
        filter = new MemberRateLimitFilter(List.of(BAR, TALK), CLOCK);
        UUID member = UUID.randomUUID();
        for (int i = 0; i <= LIMIT; i++) {
            send("POST", ORDERS, member);
        }

        for (int i = 0; i < TALK_LIMIT; i++) {
            assertEquals(200, send("POST", TALK_PATH, member).getStatus());
        }
        assertEquals(429, send("POST", TALK_PATH, member).getStatus());
    }

    @Test
    void theBudgetsStaySeparateWhateverTheOrderOfTheAreas() throws Exception {
        filter = new MemberRateLimitFilter(List.of(TALK, BAR), CLOCK);
        UUID member = UUID.randomUUID();
        for (int i = 0; i <= TALK_LIMIT; i++) {
            send("POST", TALK_PATH, member);
        }

        for (int i = 0; i < LIMIT; i++) {
            assertEquals(200, send("POST", ORDERS, member).getStatus());
        }
        assertEquals(429, send("POST", ORDERS, member).getStatus());
    }

    @Test
    void onlyTheExactTalkPathHasTheTalkBudget() throws Exception {
        filter = new MemberRateLimitFilter(List.of(BAR, TALK), CLOCK);
        UUID member = UUID.randomUUID();
        for (int i = 0; i < LIMIT; i++) {
            send("POST", TALK_PATH + "ative", member);
        }

        assertEquals(429, send("POST", TALK_PATH + "/extra", member).getStatus());
        assertEquals(200, send("POST", TALK_PATH, member).getStatus());
    }

    @Test
    void readingTheTalkPathIsNotLimited() throws Exception {
        filter = new MemberRateLimitFilter(List.of(BAR, TALK), CLOCK);
        UUID member = UUID.randomUUID();
        for (int i = 0; i < TALK_LIMIT * 3; i++) {
            assertEquals(200, send("GET", TALK_PATH, member).getStatus());
        }
    }

    @Test
    void whenTrackingIsFullTalkingIsRejectedInsteadOfLettingMoneyLeak() throws Exception {
        filter = new MemberRateLimitFilter(List.of(BAR, TALK), CLOCK, 2);
        send("POST", TALK_PATH, UUID.randomUUID());
        send("POST", TALK_PATH, UUID.randomUUID());

        assertEquals(429, send("POST", TALK_PATH, UUID.randomUUID()).getStatus());
        assertEquals(200, send("POST", ORDERS, UUID.randomUUID()).getStatus());
    }

    @Test
    void anAnonymousTalkPassesThroughSoSecurityCanAnswer401() throws Exception {
        filter = new MemberRateLimitFilter(List.of(BAR, TALK), CLOCK);
        MockFilterChain chain = null;
        for (int i = 0; i <= TALK_LIMIT; i++) {
            chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("POST", TALK_PATH), new MockHttpServletResponse(), chain);
        }

        assertNotNull(chain.getRequest());
    }

    private MockHttpServletResponse send(String method, String path, UUID member) throws Exception {
        authenticate(member);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(method, path), response, new MockFilterChain());
        SecurityContextHolder.clearContext();
        return response;
    }

    private static void authenticate(UUID member) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AccessTokenClaims(member, "USER"), null, List.of()));
    }
}

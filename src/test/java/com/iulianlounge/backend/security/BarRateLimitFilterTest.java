package com.iulianlounge.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class BarRateLimitFilterTest {

    private static final int LIMIT = 3;
    private static final String ORDERS = "/api/v1/bar/orders";
    private static final String HOUSE_CREDIT = "/api/v1/bar/house-credit";

    private BarRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new BarRateLimitFilter(LIMIT, Clock.fixed(Instant.parse("2026-10-07T20:00:00Z"), ZoneOffset.UTC));
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
        MockFilterChain chain = new MockFilterChain();
        for (int i = 0; i <= LIMIT; i++) {
            chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("POST", ORDERS), new MockHttpServletResponse(), chain);
        }

        assertTrue(chain.getRequest() != null);
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

package com.iulianlounge.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

    private static final int LIMIT = 3;
    private static final String LOGIN = "/api/v1/auth/login";

    private MovableClock clock;
    private AuthRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        clock = new MovableClock(Instant.parse("2026-09-28T12:00:00Z"));
        filter = new AuthRateLimitFilter(LIMIT, clock);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/v1/auth/login", "/api/v1/auth/register" })
    void letsRequestsThroughUpToTheLimitThenAnswers429(String path) throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            assertEquals(200, send("POST", path, "10.0.0.1").getStatus());
        }

        MockHttpServletResponse blocked = send("POST", path, "10.0.0.1");

        assertEquals(429, blocked.getStatus());
        assertTrue(blocked.getContentType().startsWith("application/problem+json"));
        assertTrue(blocked.getContentAsString().contains("\"code\":\"auth.too_many_requests\""));
        assertEquals("60", blocked.getHeader("Retry-After"));
    }

    @Test
    void blockedRequestNeverReachesTheController() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            send("POST", LOGIN, "10.0.0.1");
        }
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("POST", LOGIN, "10.0.0.1"), new MockHttpServletResponse(), chain);

        assertEquals(null, chain.getRequest());
    }

    @Test
    void windowResetsAfterAMinute() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            send("POST", LOGIN, "10.0.0.1");
        }

        clock.advance(Duration.ofSeconds(61));

        assertEquals(200, send("POST", LOGIN, "10.0.0.1").getStatus());
    }

    @Test
    void eachIpHasItsOwnCounter() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            send("POST", LOGIN, "10.0.0.1");
        }

        assertEquals(200, send("POST", LOGIN, "10.0.0.2").getStatus());
    }

    @Test
    void loginAndRegisterAreCountedSeparately() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            send("POST", LOGIN, "10.0.0.1");
        }

        assertEquals(200, send("POST", "/api/v1/auth/register", "10.0.0.1").getStatus());
    }

    @Test
    void percentEncodedPathCountsAsTheSameEndpoint() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            send("POST", LOGIN, "10.0.0.1");
        }

        assertEquals(429, send("POST", "/api/v1/auth/%6cogin", "10.0.0.1").getStatus());
    }

    @Test
    void whenTrackingIsFullNewIpsAreRejectedInsteadOfGrowingTheMap() throws Exception {
        AuthRateLimitFilter small = new AuthRateLimitFilter(LIMIT, clock, 2);
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();
        MockHttpServletResponse third = new MockHttpServletResponse();

        small.doFilter(request("POST", LOGIN, "10.0.0.1"), first, new MockFilterChain());
        small.doFilter(request("POST", LOGIN, "10.0.0.2"), second, new MockFilterChain());
        small.doFilter(request("POST", LOGIN, "10.0.0.3"), third, new MockFilterChain());

        assertEquals(200, second.getStatus());
        assertEquals(429, third.getStatus());
    }

    @Test
    void expiredEntriesFreeRoomForNewIps() throws Exception {
        AuthRateLimitFilter small = new AuthRateLimitFilter(LIMIT, clock, 1);
        small.doFilter(request("POST", LOGIN, "10.0.0.1"), new MockHttpServletResponse(), new MockFilterChain());

        clock.advance(Duration.ofSeconds(61));
        MockHttpServletResponse response = new MockHttpServletResponse();
        small.doFilter(request("POST", LOGIN, "10.0.0.2"), response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/wallet" })
    void otherPathsAreNotLimited(String path) throws Exception {
        for (int i = 0; i < LIMIT * 3; i++) {
            assertEquals(200, send("POST", path, "10.0.0.1").getStatus());
        }
    }

    @Test
    void getOnLoginIsNotCounted() throws Exception {
        for (int i = 0; i < LIMIT * 3; i++) {
            send("GET", LOGIN, "10.0.0.1");
        }

        assertEquals(200, send("POST", LOGIN, "10.0.0.1").getStatus());
    }

    private MockHttpServletResponse send(String method, String path, String ip) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(method, path, ip), response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest request(String method, String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        return request;
    }

    private static final class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}

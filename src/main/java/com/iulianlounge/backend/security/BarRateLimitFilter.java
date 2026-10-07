package com.iulianlounge.backend.security;

import java.io.IOException;
import java.time.Clock;

import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.iulianlounge.backend.exception.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class BarRateLimitFilter extends OncePerRequestFilter {

    private static final String BAR_PATH = "/api/v1/bar/";

    private final FixedWindowCounter counter;

    public BarRateLimitFilter(int maxRequestsPerWindow, Clock clock) {
        this(maxRequestsPerWindow, clock, FixedWindowCounter.DEFAULT_MAX_TRACKED_KEYS);
    }

    BarRateLimitFilter(int maxRequestsPerWindow, Clock clock, int maxTrackedKeys) {
        this.counter = new FixedWindowCounter(maxRequestsPerWindow, clock, maxTrackedKeys,
                FixedWindowCounter.WhenFull.LET_THROUGH);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !FixedWindowCounter.pathOf(request).startsWith(BAR_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccessTokenClaims claims
                && !counter.tryAcquire(claims.userId().toString())) {
            FixedWindowCounter.rejectTooManyRequests(response, ErrorCode.BAR_TOO_MANY_REQUESTS);
            return;
        }
        chain.doFilter(request, response);
    }
}

package com.iulianlounge.backend.security;

import java.io.IOException;
import java.time.Clock;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import com.iulianlounge.backend.exception.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/register");

    private final FixedWindowCounter counter;

    public AuthRateLimitFilter(int maxRequestsPerWindow, Clock clock) {
        this(maxRequestsPerWindow, clock, FixedWindowCounter.DEFAULT_MAX_TRACKED_KEYS);
    }

    AuthRateLimitFilter(int maxRequestsPerWindow, Clock clock, int maxTrackedKeys) {
        this.counter = new FixedWindowCounter(maxRequestsPerWindow, clock, maxTrackedKeys,
                FixedWindowCounter.WhenFull.REJECT);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !LIMITED_PATHS.contains(FixedWindowCounter.pathOf(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr() + " " + FixedWindowCounter.pathOf(request);
        if (!counter.tryAcquire(key)) {
            FixedWindowCounter.rejectTooManyRequests(response, ErrorCode.AUTH_TOO_MANY_REQUESTS);
            return;
        }
        chain.doFilter(request, response);
    }
}

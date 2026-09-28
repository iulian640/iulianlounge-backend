package com.iulianlounge.backend.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.iulianlounge.backend.exception.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final Set<String> LIMITED_PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/register");
    private static final String BODY = """
            {"type":"about:blank","title":"Too Many Requests","status":429,"detail":"%s","code":"%s"}"""
            .formatted(ErrorCode.AUTH_TOO_MANY_REQUESTS.detail(), ErrorCode.AUTH_TOO_MANY_REQUESTS.key());

    private final int maxRequestsPerWindow;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(int maxRequestsPerWindow, Clock clock) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant now = clock.instant();
        evictExpiredIfFull(now);
        String key = request.getRemoteAddr() + " " + request.getRequestURI();
        Window window = windows.compute(key, (k, current) -> current == null || current.isExpired(now)
                ? new Window(now, 1)
                : current.increment());

        if (window.count() > maxRequestsPerWindow) {
            reject(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private void evictExpiredIfFull(Instant now) {
        if (windows.size() >= MAX_TRACKED_KEYS) {
            windows.values().removeIf(window -> window.isExpired(now));
        }
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(WINDOW.toSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }

    private record Window(Instant start, int count) {

        boolean isExpired(Instant now) {
            return !now.isBefore(start.plus(WINDOW));
        }

        Window increment() {
            return new Window(start, count + 1);
        }
    }
}

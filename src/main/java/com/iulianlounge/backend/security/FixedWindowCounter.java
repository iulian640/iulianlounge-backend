package com.iulianlounge.backend.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.util.UrlPathHelper;

import com.iulianlounge.backend.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class FixedWindowCounter {

    static final Duration WINDOW = Duration.ofMinutes(1);
    static final int DEFAULT_MAX_TRACKED_KEYS = 10_000;
    private static final Duration SWEEP_INTERVAL = Duration.ofSeconds(1);

    private final int maxRequestsPerWindow;
    private final Clock clock;
    private final int maxTrackedKeys;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private volatile Instant lastSweep = Instant.MIN;

    FixedWindowCounter(int maxRequestsPerWindow, Clock clock, int maxTrackedKeys) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.clock = clock;
        this.maxTrackedKeys = maxTrackedKeys;
    }

    boolean tryAcquire(String key) {
        Instant now = clock.instant();
        if (!windows.containsKey(key) && isFull(now)) {
            return false;
        }
        Window window = windows.compute(key, (k, current) -> current == null || current.isExpired(now)
                ? new Window(now, 1)
                : current.increment());
        return window.count() <= maxRequestsPerWindow;
    }

    static String pathOf(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }

    static void rejectTooManyRequests(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(WINDOW.toSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,"detail":"%s","code":"%s"}"""
                .formatted(errorCode.detail(), errorCode.key()));
    }

    private boolean isFull(Instant now) {
        if (windows.size() < maxTrackedKeys) {
            return false;
        }
        if (!now.isBefore(lastSweep.plus(SWEEP_INTERVAL))) {
            lastSweep = now;
            windows.values().removeIf(window -> window.isExpired(now));
        }
        return windows.size() >= maxTrackedKeys;
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

package com.iulianlounge.backend.security;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.iulianlounge.backend.exception.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MemberRateLimitFilter extends OncePerRequestFilter {

    public record Area(String pathPrefix, int maxRequestsPerMinute, ErrorCode tooManyRequests) {
    }

    private record LimitedArea(Area area, FixedWindowCounter counter) {
    }

    private final List<LimitedArea> areas;

    public MemberRateLimitFilter(List<Area> areas, Clock clock) {
        this(areas, clock, FixedWindowCounter.DEFAULT_MAX_TRACKED_KEYS);
    }

    MemberRateLimitFilter(List<Area> areas, Clock clock, int maxTrackedKeys) {
        this.areas = areas.stream()
                .map(area -> new LimitedArea(area, new FixedWindowCounter(area.maxRequestsPerMinute(), clock,
                        maxTrackedKeys, FixedWindowCounter.WhenFull.LET_THROUGH)))
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || areaOf(request).isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Optional<LimitedArea> limited = areaOf(request);
        if (limited.isPresent() && authentication != null
                && authentication.getPrincipal() instanceof AccessTokenClaims claims
                && !limited.get().counter().tryAcquire(claims.userId().toString())) {
            FixedWindowCounter.rejectTooManyRequests(response, limited.get().area().tooManyRequests());
            return;
        }
        chain.doFilter(request, response);
    }

    private Optional<LimitedArea> areaOf(HttpServletRequest request) {
        String path = FixedWindowCounter.pathOf(request);
        return areas.stream().filter(limited -> path.startsWith(limited.area().pathPrefix())).findFirst();
    }
}

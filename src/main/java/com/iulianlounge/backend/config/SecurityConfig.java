package com.iulianlounge.backend.config;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.iulianlounge.backend.exception.ErrorCode;
import com.iulianlounge.backend.security.AuthRateLimitFilter;
import com.iulianlounge.backend.security.JwtAuthenticationFilter;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.security.MemberRateLimitFilter;
import com.iulianlounge.backend.security.ProblemDetailAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {

    private static final String BAR_PATH = "/api/v1/bar/";
    private static final String BLACKJACK_PATH = "/api/v1/blackjack/";

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
            @Value("${auth.rate-limit.max-per-minute:20}") int maxAuthAttemptsPerMinute,
            @Value("${bar.rate-limit.max-per-minute:30}") int maxBarRequestsPerMinute,
            @Value("${blackjack.rate-limit.max-per-minute:60}") int maxBlackjackRequestsPerMinute,
            ObjectProvider<Clock> clock) throws Exception {
        Clock filterClock = clock.getIfAvailable(Clock::systemUTC);
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new AuthRateLimitFilter(maxAuthAttemptsPerMinute, filterClock),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new MemberRateLimitFilter(List.of(
                        new MemberRateLimitFilter.Area(BAR_PATH, maxBarRequestsPerMinute,
                                ErrorCode.BAR_TOO_MANY_REQUESTS),
                        new MemberRateLimitFilter.Area(BLACKJACK_PATH, maxBlackjackRequestsPerMinute,
                                ErrorCode.BLACKJACK_TOO_MANY_REQUESTS)), filterClock),
                        JwtAuthenticationFilter.class);
        return http.build();
    }
}

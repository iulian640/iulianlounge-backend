package com.iulianlounge.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import com.iulianlounge.backend.config.SecurityConfig;
import com.iulianlounge.backend.exception.InvalidCredentialsException;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.service.AuthService;
import com.iulianlounge.backend.service.RegisterService;

@WebMvcTest(controllers = AuthController.class, properties = "auth.rate-limit.max-per-minute=2")
@Import(SecurityConfig.class)
class AuthRateLimitWiringTest {

    private static final String BODY = """
            {"username":"cursaito","password":"12345678"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterService registerService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void thirdLoginFromSameIpWithinAMinuteGets429() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(login()).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("auth.too_many_requests"));
    }

    private static RequestBuilder login() {
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY);
    }
}

package com.iulianlounge.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class ProblemDetailAuthenticationEntryPointTest {

    @Test
    void answers401AsProblemJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ProblemDetailAuthenticationEntryPoint().commence(
                new MockHttpServletRequest(), response, new InsufficientAuthenticationException("sin token"));

        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        assertTrue(response.getContentType().startsWith("application/problem+json"));
        assertTrue(response.getContentAsString().contains("\"code\":\"auth.required\""));
    }
}

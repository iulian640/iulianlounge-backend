package com.iulianlounge.backend.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.iulianlounge.backend.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Ruta privada sin autenticar → 401 con el mismo formato ProblemDetail que el resto de errores.
// Sin esto Spring Security contesta 403 vacío. El cuerpo es fijo: no se refleja nada de la petición.
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // Mismo code y detail que ErrorCode.AUTH_REQUIRED (ADR-06): el frontend lo trata como cualquier otro error
    private static final String BODY = """
            {"type":"about:blank","title":"Unauthorized","status":401,"detail":"%s","code":"%s"}"""
            .formatted(ErrorCode.AUTH_REQUIRED.detail(), ErrorCode.AUTH_REQUIRED.key());

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        // RFC 6750: un 401 de una API con Bearer dice qué esquema espera
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}

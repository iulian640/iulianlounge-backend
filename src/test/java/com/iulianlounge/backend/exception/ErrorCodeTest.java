package com.iulianlounge.backend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ErrorCodeTest {

    @Test
    void everyKeyIsUnique() {
        // Dos códigos con la misma clave serían indistinguibles para el frontend
        Set<String> keys = Arrays.stream(ErrorCode.values()).map(ErrorCode::key).collect(Collectors.toSet());

        assertEquals(ErrorCode.values().length, keys.size());
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void keyIsAnI18nKeyAndDetailIsNotBlank(ErrorCode code) {
        // Formato de clave de vue-i18n: grupo.nombre en minúsculas
        assertTrue(code.key().matches("[a-z]+\\.[a-z_]+"), code.key());
        assertFalse(code.detail().isBlank());
    }

    @Test
    void toProblemDetailCarriesStatusDetailAndCode() {
        ProblemDetail problem = ErrorCode.AUTH_INVALID_TOKEN.toProblemDetail();

        assertEquals(HttpStatus.UNAUTHORIZED.value(), problem.getStatus());
        assertEquals("Invalid or expired token", problem.getDetail());
        assertEquals("auth.invalid_token", problem.getProperties().get("code"));
    }
}

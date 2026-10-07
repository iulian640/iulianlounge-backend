package com.iulianlounge.backend.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.function.Executable;

final class ConstraintAssertions {

    private ConstraintAssertions() {
    }

    static void assertViolates(String constraint, Executable action) {
        RuntimeException error = assertThrows(RuntimeException.class, action);
        String message = rootMessage(error);
        assertTrue(message.contains(constraint), message);
    }

    static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }
}

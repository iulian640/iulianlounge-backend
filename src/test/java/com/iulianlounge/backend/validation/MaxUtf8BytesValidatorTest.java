package com.iulianlounge.backend.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MaxUtf8BytesValidatorTest {

    // Sin anotación real: una instancia de la interfaz basta para pasarle el límite
    private final MaxUtf8BytesValidator validator = validatorFor(72);

    @Test
    void acceptsExactlyTheLimitInBytes() {
        assertTrue(validator.isValid("ñ".repeat(36), null));   // 36 × 2 bytes = 72
    }

    @Test
    void rejectsOneByteOverTheLimit() {
        assertFalse(validator.isValid("ñ".repeat(36) + "a", null));   // 73 bytes
    }

    @Test
    void leavesNullToNotBlank() {
        assertTrue(validator.isValid(null, null));
    }

    private static MaxUtf8BytesValidator validatorFor(int maxBytes) {
        MaxUtf8BytesValidator validator = new MaxUtf8BytesValidator();
        validator.initialize(new MaxUtf8Bytes() {
            @Override
            public int value() {
                return maxBytes;
            }

            @Override
            public String message() {
                return "";
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @SuppressWarnings("unchecked")
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return MaxUtf8Bytes.class;
            }
        });
        return validator;
    }
}

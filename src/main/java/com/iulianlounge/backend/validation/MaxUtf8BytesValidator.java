package com.iulianlounge.backend.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

    private int maxBytes;

    @Override
    public void initialize(MaxUtf8Bytes annotation) {
        this.maxBytes = annotation.value();
    }

    // null lo decide @NotBlank: cada restricción comprueba una sola cosa
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}

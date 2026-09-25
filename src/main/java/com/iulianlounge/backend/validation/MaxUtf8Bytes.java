package com.iulianlounge.backend.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

// @Size cuenta caracteres; BCrypt cuenta bytes y rechaza más de 72 (una ñ o un emoji ocupan varios).
// Va en el propio campo para que el error salga bajo "password" y no bajo un método auxiliar
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "too many bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

package com.iulianlounge.backend.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// @Enumerated(STRING) guardaría "ES"; la columna ya tiene "es" y el CHECK de V3 espera minúsculas
@Converter(autoApply = true)
public class LanguageConverter implements AttributeConverter<Language, String> {

    @Override
    public String convertToDatabaseColumn(Language language) {
        return language == null ? null : language.code();
    }

    @Override
    public Language convertToEntityAttribute(String code) {
        return code == null ? null : Language.fromCode(code);
    }
}

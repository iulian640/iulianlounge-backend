package com.iulianlounge.backend.domain;

import java.util.Arrays;

// Idioma del usuario (ADR-06): solo lo usa el backend para lo que genera él (system prompt del barman).
// Se llama Language y no Locale para no chocar con java.util.Locale. En BD y en JSON va el código: "es", "en"
public enum Language {
    ES("es"),
    EN("en");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Language fromCode(String code) {
        return Arrays.stream(values())
                .filter(language -> language.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported language: " + code));
    }
}

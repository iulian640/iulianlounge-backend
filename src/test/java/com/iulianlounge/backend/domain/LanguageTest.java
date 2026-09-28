package com.iulianlounge.backend.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LanguageTest {

    @Test
    void fromCodeFindsEachSupportedLanguage() {
        assertEquals(Language.ES, Language.fromCode("es"));
        assertEquals(Language.EN, Language.fromCode("en"));
    }

    @Test
    void fromCodeRejectsAnUnsupportedCode() {
        assertThrows(IllegalArgumentException.class, () -> Language.fromCode("fr"));
    }

    @Test
    void converterStoresTheLowercaseCodeAndReadsItBack() {
        LanguageConverter converter = new LanguageConverter();

        assertEquals("es", converter.convertToDatabaseColumn(Language.ES));
        assertEquals(Language.EN, converter.convertToEntityAttribute("en"));
    }
}

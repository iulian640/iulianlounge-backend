package com.iulianlounge.backend.domain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CardsConverter implements AttributeConverter<List<Card>, String> {

    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(List<Card> cards) {
        if (cards == null) {
            return "";
        }
        return cards.stream().map(Card::code).collect(Collectors.joining(SEPARATOR));
    }

    @Override
    public List<Card> convertToEntityAttribute(String codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(codes.split(SEPARATOR)).map(Card::fromCode).toList();
    }
}

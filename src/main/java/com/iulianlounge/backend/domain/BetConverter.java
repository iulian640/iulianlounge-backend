package com.iulianlounge.backend.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BetConverter implements AttributeConverter<Bet, Long> {

    @Override
    public Long convertToDatabaseColumn(Bet bet) {
        return bet == null ? null : bet.chips();
    }

    @Override
    public Bet convertToEntityAttribute(Long chips) {
        return chips == null ? null : Bet.fromChips(chips);
    }
}

package com.iulianlounge.backend.service;

import java.util.UUID;

import com.iulianlounge.backend.dto.TalkFacts;

public interface BarFacts {

    TalkFacts factsFor(UUID userId);
}

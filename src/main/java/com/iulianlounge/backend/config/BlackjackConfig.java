package com.iulianlounge.backend.config;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.iulianlounge.backend.domain.Card;
import com.iulianlounge.backend.domain.Shuffler;

@Configuration
public class BlackjackConfig {

    @Bean
    public Shuffler shuffler() {
        SecureRandom random = new SecureRandom();
        return deck -> {
            List<Card> shuffled = new ArrayList<>(deck);
            Collections.shuffle(shuffled, random);
            return List.copyOf(shuffled);
        };
    }
}

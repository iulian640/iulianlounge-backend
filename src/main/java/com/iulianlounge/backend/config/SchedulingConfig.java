package com.iulianlounge.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.iulianlounge.backend.service.TalkMemory;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    private static final long PURGE_DELAY_MILLIS = 60_000;

    private final TalkMemory talkMemory;

    public SchedulingConfig(TalkMemory talkMemory) {
        this.talkMemory = talkMemory;
    }

    @Scheduled(fixedDelay = PURGE_DELAY_MILLIS)
    public void purgeTalkMemory() {
        talkMemory.purgeExpired();
    }
}

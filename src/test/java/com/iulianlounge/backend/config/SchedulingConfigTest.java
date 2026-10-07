package com.iulianlounge.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.iulianlounge.backend.service.TalkMemory;

class SchedulingConfigTest {

    @Test
    void everyRunPurgesTheExpiredConversations() {
        TalkMemory memory = mock(TalkMemory.class);

        new SchedulingConfig(memory).purgeTalkMemory();

        verify(memory).purgeExpired();
    }

    @Test
    void thePurgeRunsEverySixtySecondsAfterTheLastRunEnds() throws Exception {
        Scheduled scheduled = SchedulingConfig.class.getMethod("purgeTalkMemory").getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals(60_000, scheduled.fixedDelay());
    }

    @Test
    void schedulingIsEnabled() {
        assertNotNull(SchedulingConfig.class.getAnnotation(EnableScheduling.class));
    }
}

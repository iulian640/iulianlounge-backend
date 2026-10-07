package com.iulianlounge.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iulianlounge.backend.llm.LlmReply;
import com.iulianlounge.backend.llm.LlmUnavailableException;

class TalkBudgetTest {

    private static final Instant LATE_IN_MADRID = Instant.parse("2026-10-07T21:59:59Z");
    private static final Instant MIDNIGHT_IN_MADRID = Instant.parse("2026-10-07T22:00:00Z");

    private SettableClock clock;
    private AtomicInteger calls;

    @BeforeEach
    void setUp() {
        clock = new SettableClock(LATE_IN_MADRID);
        calls = new AtomicInteger();
    }

    @Test
    void aReplyCostsOneMicroDollarPerInputTokenAndFivePerOutputToken() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 8);

        budget.spend(() -> reply(900, 60));

        assertEquals(900 + 5 * 60, budget.spentToday());
    }

    @Test
    void theCostsOfSeveralRepliesAddUp() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 8);

        budget.spend(() -> reply(100, 10));
        budget.spend(() -> reply(200, 20));

        assertEquals(150 + 300, budget.spentToday());
    }

    @Test
    void onceTheDayIsSpentNothingReachesTheModel() {
        TalkBudget budget = new TalkBudget(clock, 150, 8);
        budget.spend(() -> reply(100, 10));

        LlmUnavailableException refused = assertThrows(LlmUnavailableException.class,
                () -> budget.spend(this::countedReply));

        assertEquals(LlmUnavailableException.Reason.BUDGET, refused.reason());
        assertEquals(0, calls.get());
    }

    @Test
    void aBudgetOfZeroTurnsTheModelOff() {
        TalkBudget budget = new TalkBudget(clock, 0, 8);

        assertThrows(LlmUnavailableException.class, () -> budget.spend(this::countedReply));

        assertEquals(0, calls.get());
    }

    @Test
    void aBudgetThatIsNotYetSpentStillLetsTheNextCallThrough() {
        TalkBudget budget = new TalkBudget(clock, 151, 8);
        budget.spend(() -> reply(100, 10));

        budget.spend(this::countedReply);

        assertEquals(1, calls.get());
    }

    @Test
    void theBudgetStartsOverWhenTheClubDayChangesInMadrid() {
        TalkBudget budget = new TalkBudget(clock, 150, 8);
        budget.spend(() -> reply(100, 10));
        assertThrows(LlmUnavailableException.class, () -> budget.spend(this::countedReply));

        clock.set(MIDNIGHT_IN_MADRID);
        budget.spend(this::countedReply);

        assertEquals(1, calls.get());
        assertEquals(60, budget.spentToday());
    }

    @Test
    void theDayDoesNotChangeAtUtcMidnight() {
        clock.set(Instant.parse("2026-10-07T22:30:00Z"));
        TalkBudget budget = new TalkBudget(clock, 150, 8);
        budget.spend(() -> reply(100, 10));

        clock.set(Instant.parse("2026-10-08T00:30:00Z"));

        assertEquals(150, budget.spentToday());
    }

    @Test
    void onlyAsManyCallsAsThePermitsRunAtTheSameTime() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 1);
        AtomicInteger nestedCalls = new AtomicInteger();

        budget.spend(() -> {
            LlmUnavailableException refused = assertThrows(LlmUnavailableException.class,
                    () -> budget.spend(() -> {
                        nestedCalls.incrementAndGet();
                        return reply(1, 1);
                    }));
            assertEquals(LlmUnavailableException.Reason.BUDGET, refused.reason());
            return reply(1, 1);
        });

        assertEquals(0, nestedCalls.get());
    }

    @Test
    void thePermitComesBackWhenTheCallFails() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 1);
        IllegalStateException failure = new IllegalStateException("boom");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> budget.spend(() -> {
            throw failure;
        }));
        budget.spend(this::countedReply);

        assertSame(failure, thrown);
        assertEquals(1, calls.get());
    }

    @Test
    void aFailedCallCostsNothing() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 1);

        assertThrows(IllegalStateException.class, () -> budget.spend(() -> {
            throw new IllegalStateException("boom");
        }));

        assertEquals(0, budget.spentToday());
    }

    @Test
    void thePermitComesBackAfterASuccessfulCallToo() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 1);

        budget.spend(this::countedReply);
        budget.spend(this::countedReply);

        assertEquals(2, calls.get());
    }

    private LlmReply countedReply() {
        calls.incrementAndGet();
        return reply(10, 10);
    }

    private static LlmReply reply(long input, long output) {
        return new LlmReply("ok", input, output);
    }
}

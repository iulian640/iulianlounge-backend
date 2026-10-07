package com.iulianlounge.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iulianlounge.backend.llm.LlmReply;
import com.iulianlounge.backend.llm.LlmUnavailableException;

class TalkBudgetTest {

    private static final Instant LATE_IN_MADRID = Instant.parse("2026-10-07T21:59:59Z");
    private static final Instant MIDNIGHT_IN_MADRID = Instant.parse("2026-10-07T22:00:00Z");

    private static final UUID MEMBER = UUID.randomUUID();
    private static final String IP = "203.0.113.7";

    private SettableClock clock;
    private AtomicInteger calls;

    @BeforeEach
    void setUp() {
        clock = new SettableClock(LATE_IN_MADRID);
        calls = new AtomicInteger();
    }

    @Test
    void aReplyCostsOneMicroDollarPerInputTokenAndFivePerOutputToken() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 8, 30, 60);

        budget.spend(MEMBER, IP, () -> reply(900, 60));

        assertEquals(900 + 5 * 60, budget.spentToday());
    }

    @Test
    void theCostsOfSeveralRepliesAddUp() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 8, 30, 60);

        budget.spend(MEMBER, IP, () -> reply(100, 10));
        budget.spend(MEMBER, IP, () -> reply(200, 20));

        assertEquals(150 + 300, budget.spentToday());
    }

    @Test
    void onceTheDayIsSpentNothingReachesTheModel() {
        TalkBudget budget = new TalkBudget(clock, 150, 8, 30, 60);
        budget.spend(MEMBER, IP, () -> reply(100, 10));

        LlmUnavailableException refused = assertThrows(LlmUnavailableException.class,
                () -> budget.spend(MEMBER, IP, this::countedReply));

        assertEquals(LlmUnavailableException.Reason.BUDGET, refused.reason());
        assertEquals(0, calls.get());
    }

    @Test
    void aBudgetOfZeroTurnsTheModelOff() {
        TalkBudget budget = new TalkBudget(clock, 0, 8, 30, 60);

        assertThrows(LlmUnavailableException.class, () -> budget.spend(MEMBER, IP, this::countedReply));

        assertEquals(0, calls.get());
    }

    @Test
    void aBudgetThatIsNotYetSpentStillLetsTheNextCallThrough() {
        TalkBudget budget = new TalkBudget(clock, 151, 8, 30, 60);
        budget.spend(MEMBER, IP, () -> reply(100, 10));

        budget.spend(MEMBER, IP, this::countedReply);

        assertEquals(1, calls.get());
    }

    @Test
    void theBudgetStartsOverWhenTheClubDayChangesInMadrid() {
        TalkBudget budget = new TalkBudget(clock, 150, 8, 30, 60);
        budget.spend(MEMBER, IP, () -> reply(100, 10));
        assertThrows(LlmUnavailableException.class, () -> budget.spend(MEMBER, IP, this::countedReply));

        clock.set(MIDNIGHT_IN_MADRID);
        budget.spend(MEMBER, IP, this::countedReply);

        assertEquals(1, calls.get());
        assertEquals(60, budget.spentToday());
    }

    @Test
    void theDayDoesNotChangeAtUtcMidnight() {
        clock.set(Instant.parse("2026-10-07T22:30:00Z"));
        TalkBudget budget = new TalkBudget(clock, 150, 8, 30, 60);
        budget.spend(MEMBER, IP, () -> reply(100, 10));

        clock.set(Instant.parse("2026-10-08T00:30:00Z"));

        assertEquals(150, budget.spentToday());
    }

    @Test
    void onlyAsManyCallsAsThePermitsRunAtTheSameTime() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 1, 30, 60);
        AtomicInteger nestedCalls = new AtomicInteger();

        budget.spend(MEMBER, IP, () -> {
            LlmUnavailableException refused = assertThrows(LlmUnavailableException.class,
                    () -> budget.spend(MEMBER, IP, () -> {
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
        TalkBudget budget = new TalkBudget(clock, 500_000, 1, 30, 60);
        IllegalStateException failure = new IllegalStateException("boom");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> budget.spend(MEMBER, IP, () -> {
            throw failure;
        }));
        budget.spend(MEMBER, IP, this::countedReply);

        assertSame(failure, thrown);
        assertEquals(1, calls.get());
    }

    @Test
    void aFailedCallCostsNothing() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 1, 30, 60);

        assertThrows(IllegalStateException.class, () -> budget.spend(MEMBER, IP, () -> {
            throw new IllegalStateException("boom");
        }));

        assertEquals(0, budget.spentToday());
    }

    @Test
    void thePermitComesBackAfterASuccessfulCallToo() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 1, 30, 60);

        budget.spend(MEMBER, IP, this::countedReply);
        budget.spend(MEMBER, IP, this::countedReply);

        assertEquals(2, calls.get());
    }

    @Test
    void aMemberCannotSpendMoreThanHisCapInADay() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 8, 2, 60);
        budget.spend(MEMBER, IP, this::countedReply);
        budget.spend(MEMBER, IP, this::countedReply);

        LlmUnavailableException refused = assertThrows(LlmUnavailableException.class,
                () -> budget.spend(MEMBER, IP, this::countedReply));

        assertEquals(LlmUnavailableException.Reason.MEMBER_CAP, refused.reason());
        assertEquals(2, calls.get());
    }

    @Test
    void theCapOfOneMemberDoesNotTouchAnother() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 8, 1, 60);
        budget.spend(MEMBER, IP, this::countedReply);

        budget.spend(UUID.randomUUID(), IP, this::countedReply);

        assertEquals(2, calls.get());
    }

    @Test
    void anIpCannotSpendMoreThanItsCapInADayEvenWithManyMembers() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 8, 30, 2);
        budget.spend(UUID.randomUUID(), IP, this::countedReply);
        budget.spend(UUID.randomUUID(), IP, this::countedReply);

        LlmUnavailableException refused = assertThrows(LlmUnavailableException.class,
                () -> budget.spend(UUID.randomUUID(), IP, this::countedReply));

        assertEquals(LlmUnavailableException.Reason.IP_CAP, refused.reason());
        assertEquals(2, calls.get());
    }

    @Test
    void anotherIpStillHasItsOwnCap() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 8, 30, 1);
        budget.spend(UUID.randomUUID(), IP, this::countedReply);

        budget.spend(UUID.randomUUID(), "198.51.100.4", this::countedReply);

        assertEquals(2, calls.get());
    }

    @Test
    void theCapsStartOverWhenTheClubDayChangesInMadrid() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 8, 1, 1);
        budget.spend(MEMBER, IP, this::countedReply);
        assertThrows(LlmUnavailableException.class, () -> budget.spend(MEMBER, IP, this::countedReply));

        clock.set(MIDNIGHT_IN_MADRID);
        budget.spend(MEMBER, IP, this::countedReply);

        assertEquals(2, calls.get());
    }

    @Test
    void aFailedCallStillCountsAgainstTheCaps() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 8, 2, 60);
        for (int i = 0; i < 2; i++) {
            assertThrows(IllegalStateException.class, () -> budget.spend(MEMBER, IP, () -> {
                throw new IllegalStateException("boom");
            }));
        }

        LlmUnavailableException refused = assertThrows(LlmUnavailableException.class,
                () -> budget.spend(MEMBER, IP, this::countedReply));

        assertEquals(LlmUnavailableException.Reason.MEMBER_CAP, refused.reason());
    }

    @Test
    void aMessageRefusedForLackOfAPermitDoesNotCountAgainstTheCaps() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 1, 2, 60);

        budget.spend(MEMBER, IP, () -> {
            assertThrows(LlmUnavailableException.class, () -> budget.spend(MEMBER, IP, this::countedReply));
            return reply(1, 1);
        });
        budget.spend(MEMBER, IP, this::countedReply);

        assertEquals(1, calls.get());
        LlmUnavailableException refused = assertThrows(LlmUnavailableException.class,
                () -> budget.spend(MEMBER, IP, this::countedReply));
        assertEquals(LlmUnavailableException.Reason.MEMBER_CAP, refused.reason());
    }

    @Test
    void aMessageRefusedForTheDailyBudgetDoesNotCountAgainstTheCaps() {
        TalkBudget budget = new TalkBudget(clock, 150, 8, 2, 60);
        budget.spend(MEMBER, IP, () -> reply(100, 10));
        LlmUnavailableException refused = assertThrows(LlmUnavailableException.class,
                () -> budget.spend(MEMBER, IP, this::countedReply));
        assertEquals(LlmUnavailableException.Reason.BUDGET, refused.reason());

        clock.set(MIDNIGHT_IN_MADRID);
        budget.spend(MEMBER, IP, this::countedReply);
        budget.spend(MEMBER, IP, this::countedReply);

        assertEquals(2, calls.get());
    }

    @Test
    void aMessageRefusedByTheIpCapDoesNotCountAgainstTheMember() {
        TalkBudget budget = new TalkBudget(clock, 500_000, 8, 2, 1);
        budget.spend(MEMBER, IP, this::countedReply);
        assertThrows(LlmUnavailableException.class, () -> budget.spend(MEMBER, IP, this::countedReply));

        budget.spend(MEMBER, "198.51.100.4", this::countedReply);

        assertEquals(2, calls.get());
    }

    @Test
    void theCapsHoldWhenManyThreadsSpendAtTheSameTime() throws Exception {
        TalkBudget budget = new TalkBudget(clock, 500_000, 64, 5, 60);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            tasks.add(pool.submit(() -> {
                go.await();
                try {
                    budget.spend(MEMBER, IP, this::countedReply);
                } catch (LlmUnavailableException refused) {
                    return null;
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> task : tasks) {
            task.get(10, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertEquals(5, calls.get());
    }

    private LlmReply countedReply() {
        calls.incrementAndGet();
        return reply(10, 10);
    }

    private static LlmReply reply(long input, long output) {
        return new LlmReply("ok", input, output);
    }
}

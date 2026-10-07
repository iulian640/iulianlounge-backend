package com.iulianlounge.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.iulianlounge.backend.llm.LlmReply;
import com.iulianlounge.backend.llm.LlmUnavailableException;

@Component
public class TalkBudget {

    static final long INPUT_MICROUSD_PER_TOKEN = 1;
    static final long OUTPUT_MICROUSD_PER_TOKEN = 5;

    private record Day(LocalDate date, long spentMicroUsd) {
    }

    private final Clock clock;
    private final long dailyBudgetMicroUsd;
    private final Semaphore permits;
    private final AtomicReference<Day> today;

    public TalkBudget(Clock clock,
            @Value("${barman.talk.global-budget-microusd:500000}") long dailyBudgetMicroUsd,
            @Value("${barman.talk.max-in-flight:8}") int maxInFlight) {
        this.clock = clock;
        this.dailyBudgetMicroUsd = dailyBudgetMicroUsd;
        this.permits = new Semaphore(maxInFlight);
        this.today = new AtomicReference<>(new Day(clubDay(), 0));
    }

    public LlmReply spend(Supplier<LlmReply> call) {
        if (spentToday() >= dailyBudgetMicroUsd || !permits.tryAcquire()) {
            throw new LlmUnavailableException(LlmUnavailableException.Reason.BUDGET);
        }
        try {
            LlmReply reply = call.get();
            charge(reply);
            return reply;
        } finally {
            permits.release();
        }
    }

    public long spentToday() {
        Day day = today.get();
        return day.date().equals(clubDay()) ? day.spentMicroUsd() : 0;
    }

    private void charge(LlmReply reply) {
        long cost = reply.inputTokens() * INPUT_MICROUSD_PER_TOKEN + reply.outputTokens() * OUTPUT_MICROUSD_PER_TOKEN;
        LocalDate date = clubDay();
        today.updateAndGet(day -> day.date().equals(date)
                ? new Day(date, day.spentMicroUsd() + cost)
                : new Day(date, cost));
    }

    private LocalDate clubDay() {
        return LocalDate.ofInstant(clock.instant(), BarService.CLUB_ZONE);
    }
}

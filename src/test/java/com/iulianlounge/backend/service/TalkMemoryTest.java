package com.iulianlounge.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iulianlounge.backend.llm.LlmTurn;

class TalkMemoryTest {

    private static final UUID MEMBER = UUID.randomUUID();

    private SettableClock clock;
    private TalkMemory memory;

    @BeforeEach
    void setUp() {
        clock = new SettableClock(Instant.parse("2026-10-07T20:00:00Z"));
        memory = new TalkMemory(clock);
    }

    @Test
    void aMemberWhoNeverSpokeHasNoTurns() {
        assertTrue(memory.recent(MEMBER).isEmpty());
    }

    @Test
    void aPairIsRememberedAsAUserTurnAndAnAssistantTurn() {
        memory.remember(MEMBER, "Hola", "Buenas.");

        assertEquals(List.of(user("Hola"), assistant("Buenas.")), memory.recent(MEMBER));
    }

    @Test
    void theWindowKeepsTheLastSixTurnsInPairs() {
        for (int i = 1; i <= 4; i++) {
            memory.remember(MEMBER, "pregunta " + i, "respuesta " + i);
        }

        assertEquals(List.of(
                user("pregunta 2"), assistant("respuesta 2"),
                user("pregunta 3"), assistant("respuesta 3"),
                user("pregunta 4"), assistant("respuesta 4")), memory.recent(MEMBER));
    }

    @Test
    void everyMemberHasHisOwnConversation() {
        UUID other = UUID.randomUUID();
        memory.remember(MEMBER, "mío", "tuyo");
        memory.remember(other, "otro", "otra");

        assertEquals(List.of(user("mío"), assistant("tuyo")), memory.recent(MEMBER));
        assertEquals(List.of(user("otro"), assistant("otra")), memory.recent(other));
    }

    @Test
    void theConversationExpiresFifteenMinutesAfterTheLastMessage() {
        memory.remember(MEMBER, "Hola", "Buenas.");

        clock.advance(Duration.ofMinutes(14));
        assertEquals(2, memory.recent(MEMBER).size());

        clock.advance(Duration.ofMinutes(1));
        assertTrue(memory.recent(MEMBER).isEmpty());
    }

    @Test
    void everyNewPairRestartsTheClock() {
        memory.remember(MEMBER, "uno", "uno");
        clock.advance(Duration.ofMinutes(10));
        memory.remember(MEMBER, "dos", "dos");
        clock.advance(Duration.ofMinutes(10));

        assertEquals(4, memory.recent(MEMBER).size());
    }

    @Test
    void anExpiredConversationStartsOverInsteadOfGrowing() {
        memory.remember(MEMBER, "viejo", "viejo");
        clock.advance(Duration.ofMinutes(16));

        memory.remember(MEMBER, "nuevo", "nuevo");

        assertEquals(List.of(user("nuevo"), assistant("nuevo")), memory.recent(MEMBER));
    }

    @Test
    void purgingDropsTheExpiredConversationsWithoutTouchingTheirMember() {
        memory.remember(MEMBER, "Hola", "Buenas.");
        memory.remember(UUID.randomUUID(), "Hola", "Buenas.");
        clock.advance(Duration.ofMinutes(16));

        memory.purgeExpired();

        assertEquals(0, memory.size());
    }

    @Test
    void purgingKeepsTheLiveConversations() {
        memory.remember(MEMBER, "viejo", "viejo");
        clock.advance(Duration.ofMinutes(10));
        UUID recent = UUID.randomUUID();
        memory.remember(recent, "reciente", "reciente");
        clock.advance(Duration.ofMinutes(6));

        memory.purgeExpired();

        assertEquals(1, memory.size());
        assertEquals(2, memory.recent(recent).size());
    }

    @Test
    void whenFullWithoutExpiredOnesANewMemberSimplyHasNoMemory() {
        for (int i = 0; i < TalkMemory.MAX_CONVERSATIONS; i++) {
            memory.remember(UUID.randomUUID(), "hola", "buenas");
        }
        UUID late = UUID.randomUUID();

        memory.remember(late, "hola", "buenas");

        assertEquals(TalkMemory.MAX_CONVERSATIONS, memory.size());
        assertTrue(memory.recent(late).isEmpty());
    }

    @Test
    void whenFullButSomeAreExpiredTheNewMemberTakesTheirPlace() {
        for (int i = 0; i < TalkMemory.MAX_CONVERSATIONS; i++) {
            memory.remember(UUID.randomUUID(), "hola", "buenas");
        }
        clock.advance(Duration.ofMinutes(16));
        UUID late = UUID.randomUUID();

        memory.remember(late, "hola", "buenas");

        assertEquals(1, memory.size());
        assertEquals(2, memory.recent(late).size());
    }

    @Test
    void aMemberWhoIsAlreadyInKeepsRememberingEvenWhenFull() {
        memory.remember(MEMBER, "uno", "uno");
        for (int i = 1; i < TalkMemory.MAX_CONVERSATIONS; i++) {
            memory.remember(UUID.randomUUID(), "hola", "buenas");
        }

        memory.remember(MEMBER, "dos", "dos");

        assertEquals(4, memory.recent(MEMBER).size());
    }

    @Test
    void concurrentPairsThatFitInTheWindowAreAllKept() throws Exception {
        int writers = TalkMemory.MAX_TURNS / 2;
        for (int round = 0; round < 200; round++) {
            UUID member = UUID.randomUUID();

            rememberConcurrently(member, writers);

            List<LlmTurn> window = memory.recent(member);
            assertEquals(TalkMemory.MAX_TURNS, window.size());
            assertAlternating(window);
            assertEquals(Set.of("u0", "u1", "u2"), userTexts(window));
        }
    }

    @Test
    void moreConcurrentPairsThanTheWindowHoldsLeaveItFullAndAlternating() throws Exception {
        for (int round = 0; round < 200; round++) {
            UUID member = UUID.randomUUID();

            rememberConcurrently(member, 8);

            List<LlmTurn> window = memory.recent(member);
            assertEquals(TalkMemory.MAX_TURNS, window.size());
            assertAlternating(window);
            assertEquals(TalkMemory.MAX_TURNS / 2, userTexts(window).size());
        }
    }

    @Test
    void theReturnedWindowCannotBeUsedToChangeTheMemory() {
        memory.remember(MEMBER, "Hola", "Buenas.");
        List<LlmTurn> window = memory.recent(MEMBER);

        assertThrows(UnsupportedOperationException.class, () -> window.add(user("intruso")));

        assertEquals(2, memory.recent(MEMBER).size());
    }

    private void rememberConcurrently(UUID member, int writers) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        try {
            List<Future<Void>> pending = new ArrayList<>();
            for (int writer = 0; writer < writers; writer++) {
                int id = writer;
                pending.add(pool.submit(() -> {
                    go.await();
                    memory.remember(member, "u" + id, "a" + id);
                    return null;
                }));
            }
            go.countDown();
            for (Future<Void> task : pending) {
                task.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static Set<String> userTexts(List<LlmTurn> window) {
        return window.stream()
                .filter(turn -> turn.role() == LlmTurn.Role.USER)
                .map(LlmTurn::text)
                .collect(Collectors.toSet());
    }

    private static void assertAlternating(List<LlmTurn> window) {
        assertTrue(window.size() <= TalkMemory.MAX_TURNS);
        assertEquals(0, window.size() % 2);
        for (int i = 0; i < window.size(); i++) {
            LlmTurn.Role expected = i % 2 == 0 ? LlmTurn.Role.USER : LlmTurn.Role.ASSISTANT;
            assertEquals(expected, window.get(i).role());
        }
        for (int i = 0; i < window.size(); i += 2) {
            assertEquals(window.get(i).text().substring(1), window.get(i + 1).text().substring(1));
        }
    }

    private static LlmTurn user(String text) {
        return new LlmTurn(LlmTurn.Role.USER, text);
    }

    private static LlmTurn assistant(String text) {
        return new LlmTurn(LlmTurn.Role.ASSISTANT, text);
    }
}

package com.iulianlounge.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.iulianlounge.backend.llm.LlmTurn;

@Component
public class TalkMemory {

    static final int MAX_TURNS = 6;
    static final int MAX_CONVERSATIONS = 1000;
    static final Duration TIME_TO_LIVE = Duration.ofMinutes(15);

    private static final int PAIR = 2;

    private record Conversation(List<LlmTurn> turns, Instant lastMessage) {

        boolean isExpiredAt(Instant now) {
            return !now.isBefore(lastMessage.plus(TIME_TO_LIVE));
        }
    }

    private final Clock clock;
    private final ConcurrentMap<UUID, Conversation> conversations = new ConcurrentHashMap<>();

    public TalkMemory(Clock clock) {
        this.clock = clock;
    }

    public List<LlmTurn> recent(UUID userId) {
        Conversation conversation = conversations.get(userId);
        if (conversation == null || conversation.isExpiredAt(clock.instant())) {
            return List.of();
        }
        return conversation.turns();
    }

    public void remember(UUID userId, String userText, String assistantText) {
        if (!conversations.containsKey(userId) && conversations.size() >= MAX_CONVERSATIONS) {
            purgeExpired();
            if (conversations.size() >= MAX_CONVERSATIONS) {
                return;
            }
        }
        Instant now = clock.instant();
        conversations.compute(userId, (id, current) -> {
            List<LlmTurn> window = new ArrayList<>(current == null || current.isExpiredAt(now)
                    ? List.of()
                    : current.turns());
            window.add(new LlmTurn(LlmTurn.Role.USER, userText));
            window.add(new LlmTurn(LlmTurn.Role.ASSISTANT, assistantText));
            int excess = Math.max(0, window.size() - MAX_TURNS);
            int dropped = (excess + PAIR - 1) / PAIR * PAIR;
            return new Conversation(List.copyOf(window.subList(dropped, window.size())), now);
        });
    }

    public void purgeExpired() {
        Instant now = clock.instant();
        conversations.values().removeIf(conversation -> conversation.isExpiredAt(now));
    }

    int size() {
        return conversations.size();
    }
}

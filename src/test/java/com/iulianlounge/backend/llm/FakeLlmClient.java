package com.iulianlounge.backend.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

public class FakeLlmClient implements LlmClient {

    public record Call(String systemPrompt, List<LlmTurn> turns) {
    }

    private final Deque<Supplier<LlmReply>> script = new ArrayDeque<>();
    private final List<Call> calls = new ArrayList<>();

    public synchronized FakeLlmClient willReply(String text, long inputTokens, long outputTokens) {
        LlmReply reply = new LlmReply(text, inputTokens, outputTokens);
        script.add(() -> reply);
        return this;
    }

    public synchronized FakeLlmClient willReply(String text) {
        return willReply(text, 100, 20);
    }

    public synchronized FakeLlmClient willFail(RuntimeException failure) {
        script.add(() -> {
            throw failure;
        });
        return this;
    }

    @Override
    public synchronized LlmReply reply(String systemPrompt, List<LlmTurn> turns) {
        calls.add(new Call(systemPrompt, List.copyOf(turns)));
        Supplier<LlmReply> next = script.poll();
        if (next == null) {
            throw new IllegalStateException("No reply scripted");
        }
        return next.get();
    }

    public synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    public synchronized Call lastCall() {
        return calls.get(calls.size() - 1);
    }
}

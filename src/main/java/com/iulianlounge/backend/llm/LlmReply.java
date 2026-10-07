package com.iulianlounge.backend.llm;

public record LlmReply(String text, long inputTokens, long outputTokens) {
}

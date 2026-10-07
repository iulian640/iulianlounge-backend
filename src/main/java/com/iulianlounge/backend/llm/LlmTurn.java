package com.iulianlounge.backend.llm;

public record LlmTurn(Role role, String text) {

    public enum Role { USER, ASSISTANT }
}

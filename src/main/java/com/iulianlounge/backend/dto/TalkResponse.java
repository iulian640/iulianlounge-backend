package com.iulianlounge.backend.dto;

import com.iulianlounge.backend.domain.TalkSource;

public record TalkResponse(TalkSource source, String text, String line) {

    public static TalkResponse llm(String text) {
        return new TalkResponse(TalkSource.LLM, text, null);
    }

    public static TalkResponse fallback(String line) {
        return new TalkResponse(TalkSource.FALLBACK, null, line);
    }
}

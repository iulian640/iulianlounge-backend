package com.iulianlounge.backend.llm;

import java.util.List;

public class DisabledLlmClient implements LlmClient {

    @Override
    public LlmReply reply(String systemPrompt, List<LlmTurn> turns) {
        throw new LlmUnavailableException(LlmUnavailableException.Reason.DISABLED);
    }
}

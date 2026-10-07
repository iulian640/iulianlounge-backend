package com.iulianlounge.backend.llm;

import java.util.List;

public interface LlmClient {

    LlmReply reply(String systemPrompt, List<LlmTurn> turns);
}

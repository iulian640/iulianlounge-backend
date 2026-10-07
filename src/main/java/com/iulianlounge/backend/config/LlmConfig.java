package com.iulianlounge.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.iulianlounge.backend.llm.AnthropicLlmClient;
import com.iulianlounge.backend.llm.DisabledLlmClient;
import com.iulianlounge.backend.llm.LlmClient;

@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public LlmClient llmClient(
            @Value("${barman.llm.api-key:}") String apiKey,
            @Value("${barman.llm.model:claude-haiku-4-5}") String model,
            @Value("${barman.llm.timeout:8s}") String timeout,
            @Value("${barman.llm.max-tokens:150}") long maxTokens) {
        if (apiKey.isBlank()) {
            log.info("Barman LLM disabled");
            return new DisabledLlmClient();
        }
        log.info("Barman LLM enabled");
        return AnthropicLlmClient.create(apiKey.strip(), null, DurationStyle.detectAndParse(timeout), model,
                maxTokens);
    }
}

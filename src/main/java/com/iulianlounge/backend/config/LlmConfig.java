package com.iulianlounge.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.iulianlounge.backend.llm.DisabledLlmClient;
import com.iulianlounge.backend.llm.LlmClient;

@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public LlmClient llmClient() {
        log.info("Barman LLM disabled");
        return new DisabledLlmClient();
    }
}

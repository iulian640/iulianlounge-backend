package com.iulianlounge.backend.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.iulianlounge.backend.llm.DisabledLlmClient;
import com.iulianlounge.backend.llm.LlmClient;

@ExtendWith(OutputCaptureExtension.class)
class LlmConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LlmConfig.class);

    @Test
    void withoutAKeyTheBarmanLlmIsTheClientThatIsAlwaysOff() {
        runner.run(context -> assertInstanceOf(DisabledLlmClient.class, context.getBean(LlmClient.class)));
    }

    @Test
    void startingUpSaysThatTheBarmanLlmIsDisabled(CapturedOutput output) {
        runner.run(context -> assertTrue(output.getOut().contains("Barman LLM disabled")));
    }

    @Test
    void theTestEnvironmentNeverSeesTheAnthropicKey() {
        assertNull(System.getenv("ANTHROPIC_API_KEY"));
    }
}

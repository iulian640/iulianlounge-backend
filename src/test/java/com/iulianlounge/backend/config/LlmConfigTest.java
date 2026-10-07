package com.iulianlounge.backend.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.iulianlounge.backend.llm.AnthropicLlmClient;
import com.iulianlounge.backend.llm.DisabledLlmClient;
import com.iulianlounge.backend.llm.LlmClient;

@ExtendWith(OutputCaptureExtension.class)
class LlmConfigTest {

    private static final String KEY = "sk-ant-test-0123456789";

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
    void aBlankKeyKeepsTheBarmanLlmDisabled() {
        runner.withPropertyValues("barman.llm.api-key=   ")
                .run(context -> assertInstanceOf(DisabledLlmClient.class, context.getBean(LlmClient.class)));
    }

    @Test
    void withAKeyTheBarmanLlmIsTheAnthropicClientWithoutTouchingTheNetwork(CapturedOutput output) {
        runner.withPropertyValues("barman.llm.api-key=" + KEY)
                .run(context -> {
                    LlmClient client = context.getBean(LlmClient.class);
                    assertInstanceOf(AnthropicLlmClient.class, client);
                    assertTrue(output.getOut().contains("Barman LLM enabled"));
                });
    }

    @Test
    void theKeyIsNeverLoggedNorPartOfTheClientText(CapturedOutput output) {
        runner.withPropertyValues("barman.llm.api-key=" + KEY)
                .run(context -> {
                    assertFalse(output.getAll().contains(KEY));
                    assertFalse(context.getBean(LlmClient.class).toString().contains(KEY));
                });
    }

    @Test
    void theModelTheTimeoutAndTheLimitComeFromTheProperties() {
        runner.withPropertyValues("barman.llm.api-key=" + KEY, "barman.llm.model=claude-test-model",
                "barman.llm.timeout=3s", "barman.llm.max-tokens=99")
                .run(context -> assertTrue(context.getBean(LlmClient.class).toString().contains("claude-test-model")));
    }

    @Test
    void theTestEnvironmentNeverSeesTheAnthropicKey() {
        assertNull(System.getenv("ANTHROPIC_API_KEY"));
    }
}

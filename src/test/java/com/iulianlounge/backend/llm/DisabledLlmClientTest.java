package com.iulianlounge.backend.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class DisabledLlmClientTest {

    private final LlmClient client = new DisabledLlmClient();

    @Test
    void itAlwaysRefusesWithTheDisabledReason() {
        List<LlmTurn> turns = List.of(new LlmTurn(LlmTurn.Role.USER, "hola"));

        LlmUnavailableException refused = assertThrows(LlmUnavailableException.class,
                () -> client.reply("prompt", turns));

        assertEquals(LlmUnavailableException.Reason.DISABLED, refused.reason());
        assertEquals("disabled", refused.getMessage());
    }

    @Test
    void theRefusalNeverCarriesACause() {
        LlmUnavailableException refused = assertThrows(LlmUnavailableException.class,
                () -> client.reply("prompt", List.of()));

        assertNull(refused.getCause());
    }

    @Test
    void everyReasonIsALowerCaseLogToken() {
        assertEquals("timeout", LlmUnavailableException.Reason.TIMEOUT.code());
        assertEquals("network", LlmUnavailableException.Reason.NETWORK.code());
        assertEquals("http_4xx", LlmUnavailableException.Reason.HTTP_4XX.code());
        assertEquals("http_5xx", LlmUnavailableException.Reason.HTTP_5XX.code());
        assertEquals("parse", LlmUnavailableException.Reason.PARSE.code());
        assertEquals("empty", LlmUnavailableException.Reason.EMPTY.code());
        assertEquals("budget", LlmUnavailableException.Reason.BUDGET.code());
        assertEquals("member_cap", LlmUnavailableException.Reason.MEMBER_CAP.code());
        assertEquals("ip_cap", LlmUnavailableException.Reason.IP_CAP.code());
        assertEquals("disabled", LlmUnavailableException.Reason.DISABLED.code());
    }
}

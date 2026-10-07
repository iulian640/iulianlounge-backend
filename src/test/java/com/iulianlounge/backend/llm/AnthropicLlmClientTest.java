package com.iulianlounge.backend.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class AnthropicLlmClientTest {

    private static final String KEY = "sk-ant-test-0123456789";
    private static final String MODEL = "claude-haiku-4-5";
    private static final String SYSTEM = "Eres el barman de Iulian's.";
    private static final String SECRET_TEXT = "TEXTO-SECRETO-DEL-SOCIO";
    private static final List<LlmTurn> TURNS = List.of(new LlmTurn(LlmTurn.Role.USER, SECRET_TEXT));
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private StubAnthropicServer server;
    private AnthropicLlmClient client;

    @BeforeEach
    void setUp() {
        server = new StubAnthropicServer();
        client = clientWith(Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.close();
    }

    @Test
    void aGoodAnswerComesBackAsTextAndRealTokens() {
        server.answering(200, message("Un Bee's Knees.", "end_turn", 900, 60));

        LlmReply reply = client.reply(SYSTEM, TURNS);

        assertEquals("Un Bee's Knees.", reply.text());
        assertEquals(900, reply.inputTokens());
        assertEquals(60, reply.outputTokens());
    }

    @Test
    void severalTextBlocksAreJoinedAndOtherBlocksAreIgnored() {
        server.answering(200, """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-4-5",
                "content":[{"type":"text","text":"Una."},{"type":"text","text":"Dos."}],
                "stop_reason":"end_turn","stop_sequence":null,
                "usage":{"input_tokens":10,"output_tokens":4}}
                """);

        assertEquals("Una.Dos.", client.reply(SYSTEM, TURNS).text());
    }

    @Test
    void theRequestIsAPostToMessagesWithTheKeyTheModelTheLimitAndTheRoles() throws Exception {
        server.answering(200, message("Hola.", "end_turn", 10, 4));
        List<LlmTurn> turns = List.of(
                new LlmTurn(LlmTurn.Role.USER, "Primera"),
                new LlmTurn(LlmTurn.Role.ASSISTANT, "Respuesta"),
                new LlmTurn(LlmTurn.Role.USER, "Segunda"));

        client.reply(SYSTEM, turns);

        StubAnthropicServer.Received request = server.lastRequest();
        assertEquals("POST", request.method());
        assertEquals("/v1/messages", request.path());
        assertEquals(KEY, request.headers().get("x-api-key"));
        assertTrue(request.headers().containsKey("anthropic-version"));
        JsonNode body = JSON.readTree(request.body());
        assertEquals(MODEL, body.get("model").asString());
        assertEquals(150, body.get("max_tokens").asInt());
        assertEquals(SYSTEM, body.get("system").asString());
        JsonNode messages = body.get("messages");
        assertEquals(3, messages.size());
        assertEquals("user", messages.get(0).get("role").asString());
        assertEquals("assistant", messages.get(1).get("role").asString());
        assertEquals("user", messages.get(2).get("role").asString());
        assertTrue(request.body().contains("Respuesta"));
        assertFalse(body.has("tools"));
    }

    @Test
    void everyProviderErrorStatusIsUnavailableWithoutRetrying() {
        for (int status : new int[] {400, 401, 402, 429}) {
            LlmUnavailableException failure = failureWith(status, "{\"type\":\"error\"}");
            assertEquals(LlmUnavailableException.Reason.HTTP_4XX, failure.reason(), "status " + status);
        }
        for (int status : new int[] {500, 529}) {
            LlmUnavailableException failure = failureWith(status, "{\"type\":\"error\"}");
            assertEquals(LlmUnavailableException.Reason.HTTP_5XX, failure.reason(), "status " + status);
        }
    }

    @Test
    void anOverloadedProviderReceivesExactlyOneRequest() {
        server.answering(529, "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"x\"}}");

        assertThrows(LlmUnavailableException.class, () -> client.reply(SYSTEM, TURNS));

        assertEquals(1, server.requestCount());
    }

    @Test
    void aBrokenJsonBodyIsUnavailable() {
        LlmUnavailableException failure = failureWith(200, "{not json");

        assertEquals(LlmUnavailableException.Reason.PARSE, failure.reason());
    }

    @Test
    void aMessageWithoutContentIsEmpty() {
        server.answering(200, """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-4-5","content":[],
                "stop_reason":"end_turn","stop_sequence":null,
                "usage":{"input_tokens":10,"output_tokens":0}}
                """);

        LlmUnavailableException failure = assertThrows(LlmUnavailableException.class,
                () -> client.reply(SYSTEM, TURNS));

        assertEquals(LlmUnavailableException.Reason.EMPTY, failure.reason());
    }

    @Test
    void aBlankTextIsAlsoEmpty() {
        server.answering(200, message("   ", "end_turn", 10, 1));

        LlmUnavailableException failure = assertThrows(LlmUnavailableException.class,
                () -> client.reply(SYSTEM, TURNS));

        assertEquals(LlmUnavailableException.Reason.EMPTY, failure.reason());
    }

    @Test
    void aHandlerSlowerThanTheTimeoutIsUnavailable() {
        server.answering(200, message("Tarde.", "end_turn", 10, 1)).sleeping(1500);
        client.close();
        client = clientWith(Duration.ofMillis(300));

        LlmUnavailableException failure = assertThrows(LlmUnavailableException.class,
                () -> client.reply(SYSTEM, TURNS));

        assertEquals(LlmUnavailableException.Reason.TIMEOUT, failure.reason());
        assertEquals(1, server.requestCount());
    }

    @Test
    void aProviderThatCannotBeReachedIsUnavailable() throws Exception {
        int deadPort;
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            deadPort = socket.getLocalPort();
        }
        client.close();
        client = AnthropicLlmClient.create(KEY, "http://127.0.0.1:" + deadPort, Duration.ofSeconds(2), MODEL, 150);

        LlmUnavailableException failure = assertThrows(LlmUnavailableException.class,
                () -> client.reply(SYSTEM, TURNS));

        assertEquals(LlmUnavailableException.Reason.NETWORK, failure.reason());
    }

    @Test
    void everyFailureIsWithoutCauseAndLeaksNeitherTheTextNorTheKey() {
        server.answering(401, "{\"type\":\"error\",\"error\":{\"message\":\"" + SECRET_TEXT + " " + KEY + "\"}}");

        LlmUnavailableException failure = assertThrows(LlmUnavailableException.class,
                () -> client.reply(SYSTEM, TURNS));

        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains(SECRET_TEXT));
        assertFalse(failure.getMessage().contains(KEY));
    }

    @Test
    void theLogsOnlyHaveStatusAndNumbersNeverTheTextNorTheKey(CapturedOutput output) {
        server.answering(200, message("RESPUESTA-SECRETA", "max_tokens", 900, 150));
        client.reply(SYSTEM, TURNS);
        server.answering(500, "{\"type\":\"error\",\"error\":{\"message\":\"" + SECRET_TEXT + "\"}}");
        assertThrows(LlmUnavailableException.class, () -> client.reply(SYSTEM, TURNS));

        String logs = output.getAll();

        assertTrue(logs.contains("stopReason=max_tokens"), logs);
        assertTrue(logs.contains("inputTokens=900"), logs);
        assertTrue(logs.contains("outputTokens=150"), logs);
        assertTrue(logs.contains("reason=http_5xx"), logs);
        assertTrue(logs.contains("status=500"), logs);
        assertFalse(logs.contains("RESPUESTA-SECRETA"), logs);
        assertFalse(logs.contains(SECRET_TEXT), logs);
        assertFalse(logs.contains(KEY), logs);
    }

    @Test
    void theToStringNeverShowsTheKey() {
        assertFalse(client.toString().contains(KEY));
        assertTrue(client.toString().contains(MODEL));
    }

    private LlmUnavailableException failureWith(int status, String body) {
        server.answering(status, body);
        return assertThrows(LlmUnavailableException.class, () -> client.reply(SYSTEM, TURNS));
    }

    private AnthropicLlmClient clientWith(Duration timeout) {
        return AnthropicLlmClient.create(KEY, server.baseUrl(), timeout, MODEL, 150);
    }

    private static String message(String text, String stopReason, int inputTokens, int outputTokens) {
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-4-5",
                "content":[{"type":"text","text":"%s"}],
                "stop_reason":"%s","stop_sequence":null,
                "usage":{"input_tokens":%d,"output_tokens":%d}}
                """.formatted(text, stopReason, inputTokens, outputTokens);
    }
}

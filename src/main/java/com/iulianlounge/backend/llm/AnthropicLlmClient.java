package com.iulianlounge.backend.llm;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;

public final class AnthropicLlmClient implements LlmClient, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

    private static final int NO_RETRIES = 0;
    private static final int FIRST_SERVER_ERROR = 500;
    private static final String UNKNOWN = "unknown";

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;

    private AnthropicLlmClient(AnthropicClient client, String model, long maxTokens) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    public static AnthropicLlmClient create(String apiKey, String baseUrl, Duration timeout, String model,
            long maxTokens) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(NO_RETRIES);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return new AnthropicLlmClient(builder.build(), model, maxTokens);
    }

    @Override
    public LlmReply reply(String systemPrompt, List<LlmTurn> turns) {
        long started = System.nanoTime();
        try {
            Message message = client.messages().create(request(systemPrompt, turns));
            return answer(message, started);
        } catch (LlmUnavailableException e) {
            logFailure(e.reason(), null, started);
            throw e;
        } catch (AnthropicServiceException e) {
            int status = e.statusCode();
            LlmUnavailableException.Reason reason = status >= FIRST_SERVER_ERROR
                    ? LlmUnavailableException.Reason.HTTP_5XX
                    : LlmUnavailableException.Reason.HTTP_4XX;
            logFailure(reason, status, started);
            throw new LlmUnavailableException(reason);
        } catch (AnthropicIoException e) {
            LlmUnavailableException.Reason reason = isTimeout(e)
                    ? LlmUnavailableException.Reason.TIMEOUT
                    : LlmUnavailableException.Reason.NETWORK;
            logFailure(reason, null, started);
            throw new LlmUnavailableException(reason);
        } catch (RuntimeException e) {
            logFailure(LlmUnavailableException.Reason.PARSE, null, started);
            throw new LlmUnavailableException(LlmUnavailableException.Reason.PARSE);
        }
    }

    @Override
    public void close() {
        client.close();
    }

    @Override
    public String toString() {
        return "AnthropicLlmClient[model=" + model + "]";
    }

    private MessageCreateParams request(String systemPrompt, List<LlmTurn> turns) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(systemPrompt);
        for (LlmTurn turn : turns) {
            if (turn.role() == LlmTurn.Role.USER) {
                builder.addUserMessage(turn.text());
            } else {
                builder.addAssistantMessage(turn.text());
            }
        }
        return builder.build();
    }

    private LlmReply answer(Message message, long started) {
        String text = message.content().stream()
                .map(ContentBlock::text)
                .flatMap(Optional::stream)
                .map(TextBlock::text)
                .collect(Collectors.joining());
        if (text.isBlank()) {
            throw new LlmUnavailableException(LlmUnavailableException.Reason.EMPTY);
        }
        long inputTokens = message.usage().inputTokens();
        long outputTokens = message.usage().outputTokens();
        String stopReason = message.stopReason().map(StopReason::asString).orElse(UNKNOWN);
        log.info("Barman LLM call result=ok stopReason={} latencyMs={} inputTokens={} outputTokens={}",
                stopReason, millisSince(started), inputTokens, outputTokens);
        return new LlmReply(text, inputTokens, outputTokens);
    }

    private static void logFailure(LlmUnavailableException.Reason reason, Integer status, long started) {
        log.warn("Barman LLM call result=failed reason={} status={} latencyMs={}",
                reason.code(), status == null ? "none" : status, millisSince(started));
    }

    private static long millisSince(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private static boolean isTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }
}

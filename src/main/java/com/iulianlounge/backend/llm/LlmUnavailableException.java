package com.iulianlounge.backend.llm;

import java.util.Locale;

public class LlmUnavailableException extends RuntimeException {

    public enum Reason {
        DISABLED, TIMEOUT, NETWORK, HTTP_4XX, HTTP_5XX, PARSE, EMPTY, BUDGET;

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Reason reason;

    public LlmUnavailableException(Reason reason) {
        super(reason.code());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}

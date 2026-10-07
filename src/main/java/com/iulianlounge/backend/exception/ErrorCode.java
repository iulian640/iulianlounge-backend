package com.iulianlounge.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public enum ErrorCode {

    AUTH_REQUIRED("auth.required", HttpStatus.UNAUTHORIZED, "Authentication required"),
    AUTH_INVALID_CREDENTIALS("auth.invalid_credentials", HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    AUTH_INVALID_TOKEN("auth.invalid_token", HttpStatus.UNAUTHORIZED, "Invalid or expired token"),
    AUTH_TOO_MANY_REQUESTS("auth.too_many_requests", HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, try again later"),

    USER_USERNAME_TAKEN("user.username_taken", HttpStatus.CONFLICT, "Username already in use"),
    USER_EMAIL_TAKEN("user.email_taken", HttpStatus.CONFLICT, "Email already in use"),
    USER_ALREADY_EXISTS("user.already_exists", HttpStatus.CONFLICT, "Username or email already in use"),

    WALLET_NOT_FOUND("wallet.not_found", HttpStatus.NOT_FOUND, "Wallet not found"),
    WALLET_INSUFFICIENT_FUNDS("wallet.insufficient_funds", HttpStatus.UNPROCESSABLE_CONTENT, "Insufficient funds"),
    WALLET_CONFLICT("wallet.conflict", HttpStatus.CONFLICT, "Wallet was updated concurrently, try again"),
    WALLET_IDEMPOTENCY_MISMATCH("wallet.idempotency_mismatch", HttpStatus.CONFLICT,
            "Idempotency key already used for a different movement"),

    BAR_CREDIT_NOT_NEEDED("bar.credit_not_needed", HttpStatus.UNPROCESSABLE_CONTENT,
            "House credit is only for a wallet that cannot pay the cheapest drink"),
    BAR_CREDIT_USED_TODAY("bar.credit_used_today", HttpStatus.CONFLICT, "House credit already given today"),
    BAR_TOO_MANY_REQUESTS("bar.too_many_requests", HttpStatus.TOO_MANY_REQUESTS, "Too many bar requests, try again later"),

    BLACKJACK_HAND_IN_PROGRESS("blackjack.hand_in_progress", HttpStatus.CONFLICT, "A hand is already in progress"),
    BLACKJACK_HAND_NOT_FOUND("blackjack.hand_not_found", HttpStatus.NOT_FOUND, "Hand not found"),
    BLACKJACK_HAND_FINISHED("blackjack.hand_finished", HttpStatus.CONFLICT, "The hand is already finished"),
    BLACKJACK_TOO_MANY_REQUESTS("blackjack.too_many_requests", HttpStatus.TOO_MANY_REQUESTS,
            "Too many blackjack requests, try again later"),

    DATA_CONFLICT("data.conflict", HttpStatus.CONFLICT, "Data conflict"),

    VALIDATION_FAILED("validation.failed", HttpStatus.BAD_REQUEST, "Request validation failed"),
    REQUEST_REJECTED("request.rejected", HttpStatus.BAD_REQUEST, "Request rejected"),

    INTERNAL_ERROR("internal.error", HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    public static final String PROPERTY = "code";

    private final String key;
    private final HttpStatus status;
    private final String detail;

    ErrorCode(String key, HttpStatus status, String detail) {
        this.key = key;
        this.status = status;
        this.detail = detail;
    }

    public String key() {
        return key;
    }

    public HttpStatus status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public ProblemDetail toProblemDetail() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(PROPERTY, key);
        return problem;
    }
}

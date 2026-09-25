package com.iulianlounge.backend.exception;

public class IdempotencyMismatchException extends ApiException {
    public IdempotencyMismatchException() {
        super(ErrorCode.WALLET_IDEMPOTENCY_MISMATCH);
    }
}

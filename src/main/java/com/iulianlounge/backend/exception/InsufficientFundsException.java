package com.iulianlounge.backend.exception;

public class InsufficientFundsException extends ApiException {
    public InsufficientFundsException() {
        super(ErrorCode.WALLET_INSUFFICIENT_FUNDS);
    }
}

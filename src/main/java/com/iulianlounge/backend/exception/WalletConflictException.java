package com.iulianlounge.backend.exception;

public class WalletConflictException extends ApiException {
    public WalletConflictException() {
        super(ErrorCode.WALLET_CONFLICT);
    }
}

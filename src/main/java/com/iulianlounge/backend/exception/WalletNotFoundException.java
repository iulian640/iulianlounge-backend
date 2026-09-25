package com.iulianlounge.backend.exception;

public class WalletNotFoundException extends ApiException {
    public WalletNotFoundException() {
        super(ErrorCode.WALLET_NOT_FOUND);
    }
}

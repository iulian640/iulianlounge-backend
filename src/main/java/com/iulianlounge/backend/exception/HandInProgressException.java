package com.iulianlounge.backend.exception;

public class HandInProgressException extends ApiException {
    public HandInProgressException() {
        super(ErrorCode.BLACKJACK_HAND_IN_PROGRESS);
    }
}

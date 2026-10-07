package com.iulianlounge.backend.exception;

public class HandFinishedException extends ApiException {
    public HandFinishedException() {
        super(ErrorCode.BLACKJACK_HAND_FINISHED);
    }
}

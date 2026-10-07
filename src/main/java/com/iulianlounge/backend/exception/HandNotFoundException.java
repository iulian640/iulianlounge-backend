package com.iulianlounge.backend.exception;

public class HandNotFoundException extends ApiException {
    public HandNotFoundException() {
        super(ErrorCode.BLACKJACK_HAND_NOT_FOUND);
    }
}

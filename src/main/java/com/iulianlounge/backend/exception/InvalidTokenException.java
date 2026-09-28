package com.iulianlounge.backend.exception;

public class InvalidTokenException extends ApiException {
    public InvalidTokenException() {
        super(ErrorCode.AUTH_INVALID_TOKEN);
    }
}

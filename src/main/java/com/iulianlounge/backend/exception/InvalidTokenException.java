package com.iulianlounge.backend.exception;

public class InvalidTokenException extends ApiException {
    public InvalidTokenException() {
        // Un único código para todo token rechazado: no revela si la firma era buena
        super(ErrorCode.AUTH_INVALID_TOKEN);
    }
}

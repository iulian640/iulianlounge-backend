package com.iulianlounge.backend.exception;

public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException() {
        // Mismo código para usuario inexistente y contraseña mala: no revela qué usernames existen
        super(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
}

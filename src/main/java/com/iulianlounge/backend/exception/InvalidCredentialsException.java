package com.iulianlounge.backend.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        // Mismo mensaje para usuario inexistente y contraseña mala: no revela qué usernames existen
        super("Usuario o contraseña incorrectos");
    }
}

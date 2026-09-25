package com.iulianlounge.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

// ADR-06: lista COMPLETA de errores que el frontend tiene que traducir. La key viaja en el campo "code"
// y es estable: cambiarla rompe el frontend. detail va en inglés y solo sirve para depurar
public enum ErrorCode {

    AUTH_REQUIRED("auth.required", HttpStatus.UNAUTHORIZED, "Authentication required"),
    // Mismo código para usuario inexistente y contraseña mala: no revela qué usernames existen
    AUTH_INVALID_CREDENTIALS("auth.invalid_credentials", HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    // Uno solo para todo token rechazado: no revela si la firma era buena
    AUTH_INVALID_TOKEN("auth.invalid_token", HttpStatus.UNAUTHORIZED, "Invalid or expired token"),

    USER_USERNAME_TAKEN("user.username_taken", HttpStatus.CONFLICT, "Username already in use"),
    USER_EMAIL_TAKEN("user.email_taken", HttpStatus.CONFLICT, "Email already in use"),
    // Carrera entre dos registros: el UNIQUE de la BD no dice cuál de los dos campos chocó
    USER_ALREADY_EXISTS("user.already_exists", HttpStatus.CONFLICT, "Username or email already in use"),

    // Una cuenta sin cartera: no debería pasar (V4 dio una a los usuarios antiguos), pero que falle claro
    WALLET_NOT_FOUND("wallet.not_found", HttpStatus.NOT_FOUND, "Wallet not found"),
    // 422: la petición es correcta, pero no hay fichas para pagarla
    WALLET_INSUFFICIENT_FUNDS("wallet.insufficient_funds", HttpStatus.UNPROCESSABLE_CONTENT, "Insufficient funds"),
    // Dos escrituras a la vez sobre la misma cartera y el reintento también chocó (ADR-04)
    WALLET_CONFLICT("wallet.conflict", HttpStatus.CONFLICT, "Wallet was updated concurrently, try again"),
    // Una clave de idempotencia reutilizada para otra operación (otra cantidad o tipo): bug del cliente
    WALLET_IDEMPOTENCY_MISMATCH("wallet.idempotency_mismatch", HttpStatus.CONFLICT,
            "Idempotency key already used for a different movement"),

    // Cualquier otra violación de la BD (FK, CHECK...): no le decimos al usuario qué chocó
    DATA_CONFLICT("data.conflict", HttpStatus.CONFLICT, "Data conflict"),

    VALIDATION_FAILED("validation.failed", HttpStatus.BAD_REQUEST, "Request validation failed"),
    // Errores que genera Spring (JSON roto, 415...): se usa solo la key, el status real lo pone Spring
    REQUEST_REJECTED("request.rejected", HttpStatus.BAD_REQUEST, "Request rejected"),

    // Lo no previsto. El detalle va al log del servidor, nunca al cliente
    INTERNAL_ERROR("internal.error", HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    public static final String PROPERTY = "code";

    private final String key;
    private final HttpStatus status;
    private final String detail;

    ErrorCode(String key, HttpStatus status, String detail) {
        this.key = key;
        this.status = status;
        this.detail = detail;
    }

    public String key() {
        return key;
    }

    public HttpStatus status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public ProblemDetail toProblemDetail() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(PROPERTY, key);
        return problem;
    }
}

package com.iulianlounge.backend.exception;

// Base de los errores de la app: cada uno lleva su ErrorCode y el handler lo convierte en ProblemDetail.
// Añadir un error nuevo obliga a darle un código (ADR-06)
public abstract class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ApiException(ErrorCode errorCode) {
        super(errorCode.detail());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

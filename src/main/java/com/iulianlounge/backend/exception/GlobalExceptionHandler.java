package com.iulianlounge.backend.exception;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.iulianlounge.backend.security.RefreshCookies;

// ADR-06: todo error sale como ProblemDetail con "code" (lo que traduce el frontend) y detail en inglés
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final List<String> CONSTRAINT_PRIORITY =
            List.of("NotNull", "NotBlank", "Size", "Pattern", "Email", "MaxUtf8Bytes");

    // Frontera de palabra en CamelCase, también con acrónimos: MaxUTF8Bytes → Max_UTF8_Bytes
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        return ex.getErrorCode().toProblemDetail();
    }

    // Refresh rechazado (caducado, cuenta borrada...): además se borra la cookie muerta,
    // o el navegador la seguiría mandando en cada carga hasta su Max-Age
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidToken(InvalidTokenException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.status())
                .header(HttpHeaders.SET_COOKIE, RefreshCookies.clear().toString())
                .body(code.toProblemDetail());
    }

    // La carrera del registro ya la traduce RegisterService; lo que llegue aquí es otra violación
    // (FK, CHECK...) y no debe decir "el usuario ya existe"
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ErrorCode.DATA_CONFLICT.toProblemDetail();
    }

    // Todo lo no previsto: 500 con code, y el detalle (stack, mensaje) solo al log
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) throws Exception {
        // Las de Spring Security las decide su propia cadena (401/403), no un 500
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Unhandled exception", ex);
        return ErrorCode.INTERNAL_ERROR.toProblemDetail();
    }

    // 400 de @Valid con "errors": {campo: clave} para que el frontend marque cada input en su idioma
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Un campo puede fallar varias restricciones y el validador las da sin orden fijo:
        // se queda la de más prioridad para que la clave no cambie entre peticiones
        Map<String, FieldError> firstByField = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> firstByField.merge(error.getField(), error,
                (current, candidate) -> priority(candidate) < priority(current) ? candidate : current));

        Map<String, String> errors = new LinkedHashMap<>();
        firstByField.forEach((field, error) -> errors.put(field, validationKey(error)));

        ProblemDetail problem = ErrorCode.VALIDATION_FAILED.toProblemDetail();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // Errores que genera Spring (JSON roto, 405, 415...): mantienen su status pero también llevan code
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem && !hasCode(problem)) {
            problem.setProperty(ErrorCode.PROPERTY, ErrorCode.REQUEST_REJECTED.key());
        }
        return response;
    }

    private static boolean hasCode(ProblemDetail problem) {
        return problem.getProperties() != null && problem.getProperties().containsKey(ErrorCode.PROPERTY);
    }

    // De lo más básico a lo más fino: si falta el valor, eso es lo primero que hay que decir
    private static int priority(FieldError error) {
        int index = CONSTRAINT_PRIORITY.indexOf(error.getCode());
        return index < 0 ? CONSTRAINT_PRIORITY.size() : index;
    }

    // Nombre de la restricción → clave: NotBlank → validation.not_blank, MaxUtf8Bytes → validation.max_utf8_bytes.
    // Nunca el mensaje del validador: cambia según el Accept-Language
    private static String validationKey(FieldError error) {
        String constraint = error.getCode() == null ? "invalid" : error.getCode();
        return "validation." + CAMEL_BOUNDARY.matcher(constraint).replaceAll("_").toLowerCase(Locale.ROOT);
    }
}

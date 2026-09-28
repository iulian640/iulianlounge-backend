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

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final List<String> CONSTRAINT_PRIORITY =
            List.of("NotNull", "NotBlank", "Size", "Pattern", "Email", "MaxUtf8Bytes");

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        return ex.getErrorCode().toProblemDetail();
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidToken(InvalidTokenException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.status())
                .header(HttpHeaders.SET_COOKIE, RefreshCookies.clear().toString())
                .body(code.toProblemDetail());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ErrorCode.DATA_CONFLICT.toProblemDetail();
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Unhandled exception", ex);
        return ErrorCode.INTERNAL_ERROR.toProblemDetail();
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, FieldError> firstByField = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> firstByField.merge(error.getField(), error,
                (current, candidate) -> priority(candidate) < priority(current) ? candidate : current));

        Map<String, String> errors = new LinkedHashMap<>();
        firstByField.forEach((field, error) -> errors.put(field, validationKey(error)));

        ProblemDetail problem = ErrorCode.VALIDATION_FAILED.toProblemDetail();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

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

    private static int priority(FieldError error) {
        int index = CONSTRAINT_PRIORITY.indexOf(error.getCode());
        return index < 0 ? CONSTRAINT_PRIORITY.size() : index;
    }

    private static String validationKey(FieldError error) {
        String constraint = error.getCode() == null ? "invalid" : error.getCode();
        return "validation." + CAMEL_BOUNDARY.matcher(constraint).replaceAll("_").toLowerCase(Locale.ROOT);
    }
}

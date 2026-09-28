package com.iulianlounge.backend.exception;

public class DuplicateUserException extends ApiException {
    public DuplicateUserException(ErrorCode errorCode) {
        super(errorCode);
    }
}

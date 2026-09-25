package com.iulianlounge.backend.exception;

// USER_USERNAME_TAKEN o USER_EMAIL_TAKEN: el service sabe cuál de los dos chocó
public class DuplicateUserException extends ApiException {
    public DuplicateUserException(ErrorCode errorCode) {
        super(errorCode);
    }
}

package com.iulianlounge.backend.exception;

public class HouseCreditUsedTodayException extends ApiException {
    public HouseCreditUsedTodayException() {
        super(ErrorCode.BAR_CREDIT_USED_TODAY);
    }
}

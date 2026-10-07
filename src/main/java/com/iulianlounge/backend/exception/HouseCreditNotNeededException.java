package com.iulianlounge.backend.exception;

public class HouseCreditNotNeededException extends ApiException {
    public HouseCreditNotNeededException() {
        super(ErrorCode.BAR_CREDIT_NOT_NEEDED);
    }
}

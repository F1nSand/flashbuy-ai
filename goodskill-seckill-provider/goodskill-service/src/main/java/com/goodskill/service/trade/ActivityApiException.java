package com.goodskill.service.trade;

public class ActivityApiException extends RuntimeException {

    private final int status;
    private final String errorCode;

    public ActivityApiException(int status, String errorCode) {
        super(errorCode);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }
}

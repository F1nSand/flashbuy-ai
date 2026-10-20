package com.goodskill.service.trade;

public class TradeFileApiException extends RuntimeException {

    private final int status;
    private final String errorCode;

    public TradeFileApiException(int status, String errorCode) {
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

package com.goodskill.service.trade;

public class PaymentChannelException extends RuntimeException {

    public PaymentChannelException(String message) {
        super(message);
    }

    public PaymentChannelException(String message, Throwable cause) {
        super(message, cause);
    }
}

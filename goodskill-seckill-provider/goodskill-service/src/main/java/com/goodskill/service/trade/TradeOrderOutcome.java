package com.goodskill.service.trade;

public record TradeOrderOutcome(TradeOrderResponse order, String errorCode, int status) {

    public static TradeOrderOutcome success(TradeOrderResponse response) {
        return new TradeOrderOutcome(response, null, 200);
    }

    public static TradeOrderOutcome rejected(String errorCode, int status) {
        return new TradeOrderOutcome(null, errorCode, status);
    }

    public boolean succeeded() {
        return order != null;
    }
}

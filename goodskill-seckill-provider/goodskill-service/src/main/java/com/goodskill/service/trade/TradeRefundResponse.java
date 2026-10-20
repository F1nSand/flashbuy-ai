package com.goodskill.service.trade;

public record TradeRefundResponse(String refundNo, String orderNo, long amountMinor,
                                  String currency, String state) {
}

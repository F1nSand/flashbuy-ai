package com.goodskill.service.trade;

public record TradePaymentCheckoutResponse(String paymentNo, long amountMinor, String currency,
                                           String formHtml) {
}

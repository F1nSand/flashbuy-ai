package com.goodskill.service.trade;

import java.time.Instant;

public record TradeOrderResponse(
        String orderNo,
        String state,
        Instant paymentDeadline,
        long totalAmountMinor,
        String currency,
        boolean replayed) {
}

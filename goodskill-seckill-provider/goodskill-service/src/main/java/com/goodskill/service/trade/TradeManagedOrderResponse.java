package com.goodskill.service.trade;

import java.time.Instant;

public record TradeManagedOrderResponse(
        String orderNo, String buyerId, long activityId, String productName, String skuName, int quantity,
        long unitPriceMinor, long totalAmountMinor, String currency, String state, String fulfillmentState,
        Instant paymentDeadline, Instant createdAt) {
}

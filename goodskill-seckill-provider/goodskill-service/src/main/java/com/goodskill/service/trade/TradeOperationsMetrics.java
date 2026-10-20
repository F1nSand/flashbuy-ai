package com.goodskill.service.trade;

public record TradeOperationsMetrics(long activeActivities, long pendingPaymentOrders, long paidOrders,
                                     long waitingFulfillmentOrders, long reservedUnits, long availableUnits) {
}

package com.goodskill.service.trade;

public record TradeOutboxEvent(String eventId, String aggregateType, String aggregateId,
                               String eventType, int eventVersion, String payload, int attemptCount) {
}

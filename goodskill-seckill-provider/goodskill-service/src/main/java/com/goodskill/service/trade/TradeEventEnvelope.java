package com.goodskill.service.trade;

public record TradeEventEnvelope(String eventId, String aggregateType, String aggregateId,
                                 String eventType, int eventVersion, String payload) {
}

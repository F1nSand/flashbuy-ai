package com.goodskill.web.realtime;

public record TradeRealtimeEvent(String eventId, String eventType, String orderNo, String state) {
}

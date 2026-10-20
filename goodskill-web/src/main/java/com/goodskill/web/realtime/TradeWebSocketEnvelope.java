package com.goodskill.web.realtime;

public record TradeWebSocketEnvelope(String buyerId, TradeRealtimeEvent event) {
}

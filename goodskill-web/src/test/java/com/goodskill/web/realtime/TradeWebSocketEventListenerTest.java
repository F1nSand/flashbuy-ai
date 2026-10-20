package com.goodskill.web.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TradeWebSocketEventListenerTest {

    @Test
    void projectsAnOrderEventToMinimalBuyerScopedRealtimeData() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        TradeWebSocketEventListener listener = new TradeWebSocketEventListener(new ObjectMapper(), redis);
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("aabbccdd-eeff-4aab-8ccd-001122334455");
        properties.setHeader("eventType", "OrderPaid");
        Message message = new Message(("{\"orderNo\":\"GS0123456789ABCDEF0123456789ABCDEF\","
                + "\"buyerId\":\"buyer-1\",\"providerTradeNo\":\"must-not-be-forwarded\"}")
                .getBytes(StandardCharsets.UTF_8), properties);

        listener.onCommerceEvent(message);

        verify(redis).convertAndSend(eq(TradeWebSocketEventConfiguration.REDIS_CHANNEL),
                contains("\"eventType\":\"OrderPaid\""));
        verify(redis).convertAndSend(eq(TradeWebSocketEventConfiguration.REDIS_CHANNEL),
                contains("\"state\":\"PAID\""));
        org.mockito.ArgumentCaptor<String> payload = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(redis).convertAndSend(eq(TradeWebSocketEventConfiguration.REDIS_CHANNEL),
                payload.capture());
        org.junit.jupiter.api.Assertions.assertFalse(payload.getValue().contains("providerTradeNo"));
    }

    @Test
    void preservesClosedStateForLatePaymentRefundEvents() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        TradeWebSocketEventListener listener = new TradeWebSocketEventListener(new ObjectMapper(), redis);
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("aabbccdd-eeff-4aab-8ccd-001122334455");
        properties.setHeader("eventType", "OrderRefunded");
        Message message = new Message(("{\"orderNo\":\"GS0123456789ABCDEF0123456789ABCDEF\","
                + "\"buyerId\":\"buyer-1\",\"orderState\":\"CLOSED\"}")
                .getBytes(StandardCharsets.UTF_8), properties);

        listener.onCommerceEvent(message);

        org.mockito.ArgumentCaptor<String> payload = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(redis).convertAndSend(eq(TradeWebSocketEventConfiguration.REDIS_CHANNEL),
                payload.capture());
        org.junit.jupiter.api.Assertions.assertTrue(payload.getValue().contains("\"state\":\"CLOSED\""));
    }
}

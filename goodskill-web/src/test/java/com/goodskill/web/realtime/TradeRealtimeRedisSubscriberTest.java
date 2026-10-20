package com.goodskill.web.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeRealtimeRedisSubscriberTest {

    @Test
    void deliversOnlyToTheAuthenticatedBuyersUserDestination() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        TradeRealtimeRedisSubscriber subscriber = new TradeRealtimeRedisSubscriber(new ObjectMapper(), messaging);
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(("{\"buyerId\":\"buyer-1\",\"event\":{"
                + "\"eventId\":\"aabbccdd-eeff-4aab-8ccd-001122334455\","
                + "\"eventType\":\"OrderPaid\",\"orderNo\":\"GS0123456789ABCDEF0123456789ABCDEF\","
                + "\"state\":\"PAID\"}}")
                .getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(message, null);

        org.mockito.ArgumentCaptor<TradeRealtimeEvent> event =
                org.mockito.ArgumentCaptor.forClass(TradeRealtimeEvent.class);
        verify(messaging).convertAndSendToUser(eq("buyer-1"), eq("/queue/trade-events"), event.capture());
        org.junit.jupiter.api.Assertions.assertEquals("OrderPaid", event.getValue().eventType());
    }

    @Test
    void dropsMalformedOrUnexpectedStates() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        TradeRealtimeRedisSubscriber subscriber = new TradeRealtimeRedisSubscriber(new ObjectMapper(), messaging);
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(("{\"buyerId\":\"buyer-1\",\"event\":{"
                + "\"eventId\":\"aabbccdd-eeff-4aab-8ccd-001122334455\","
                + "\"eventType\":\"OrderPaid\",\"orderNo\":\"GS0123456789ABCDEF0123456789ABCDEF\","
                + "\"state\":\"REFUNDED\"}}")
                .getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(message, null);

        verify(messaging, never()).convertAndSendToUser(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }
}

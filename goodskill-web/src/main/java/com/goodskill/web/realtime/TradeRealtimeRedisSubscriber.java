package com.goodskill.web.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Profile("production")
public class TradeRealtimeRedisSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(TradeRealtimeRedisSubscriber.class);
    private static final Pattern BUYER_ID = Pattern.compile("[A-Za-z0-9._:@+-]{1,64}");
    private static final Pattern ORDER_NO = Pattern.compile("GS[0-9A-F]{32}");
    private static final Pattern EVENT_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");
    private static final Map<String, String> EVENT_STATES = Map.of(
            "OrderCreated", "PENDING_PAYMENT",
            "OrderPaid", "PAID",
            "OrderClosed", "CLOSED",
            "OrderRefunded", "REFUNDED",
            "OrderRefundFailed", "PAID");

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public TradeRealtimeRedisSubscriber(ObjectMapper objectMapper, SimpMessagingTemplate messagingTemplate) {
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            TradeWebSocketEnvelope envelope = objectMapper.readValue(message.getBody(), TradeWebSocketEnvelope.class);
            if (envelope == null || envelope.event() == null || envelope.buyerId() == null
                    || !BUYER_ID.matcher(envelope.buyerId()).matches()
                    || envelope.event().eventId() == null || !EVENT_ID.matcher(envelope.event().eventId()).matches()
                    || envelope.event().orderNo() == null || !ORDER_NO.matcher(envelope.event().orderNo()).matches()
                    || envelope.event().eventType() == null
                    || !EVENT_STATES.containsKey(envelope.event().eventType())
                    || !isAllowedState(envelope.event())) {
                throw new IllegalArgumentException("Invalid realtime commerce event");
            }
            messagingTemplate.convertAndSendToUser(envelope.buyerId(), "/queue/trade-events", envelope.event());
        } catch (IOException | IllegalArgumentException invalidEvent) {
            log.warn("Discarded malformed realtime commerce event; reason={}", invalidEvent.getClass().getSimpleName());
        }
    }

    private static boolean isAllowedState(TradeRealtimeEvent event) {
        String state = event.state();
        if ("OrderRefunded".equals(event.eventType())) {
            return "REFUNDED".equals(state) || "CLOSED".equals(state);
        }
        if ("OrderRefundFailed".equals(event.eventType())) {
            return "PAID".equals(state) || "CLOSED".equals(state);
        }
        return EVENT_STATES.get(event.eventType()).equals(state);
    }
}

package com.goodskill.web.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@Profile("production")
public class TradeWebSocketEventListener {

    private static final Pattern EVENT_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");
    private static final Pattern BUYER_ID = Pattern.compile("[A-Za-z0-9._:@+-]{1,64}");
    private static final Pattern ORDER_NO = Pattern.compile("GS[0-9A-F]{32}");
    private static final Set<String> ORDER_EVENTS = Set.of(
            "OrderCreated", "OrderPaid", "OrderClosed", "OrderRefunded", "OrderRefundFailed");

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public TradeWebSocketEventListener(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @RabbitListener(queues = TradeWebSocketEventConfiguration.QUEUE)
    public void onCommerceEvent(Message message) {
        try {
            String eventId = message.getMessageProperties().getMessageId();
            Object headerEventType = message.getMessageProperties().getHeaders().get("eventType");
            if (!(headerEventType instanceof String eventType)) {
                throw new IllegalArgumentException("Commerce event is missing an event type");
            }
            if (!ORDER_EVENTS.contains(eventType)) {
                return;
            }
            if (eventId == null || !EVENT_ID.matcher(eventId).matches()) {
                throw new IllegalArgumentException("Commerce event is missing a valid event id");
            }
            JsonNode payload = objectMapper.readTree(message.getBody());
            String buyerId = text(payload, "buyerId");
            String orderNo = text(payload, "orderNo");
            if (!BUYER_ID.matcher(buyerId).matches() || !ORDER_NO.matcher(orderNo).matches()) {
                throw new IllegalArgumentException("Commerce event identity is invalid");
            }
            String state = projectedState(eventType, payload);
            TradeRealtimeEvent event = new TradeRealtimeEvent(eventId, eventType, orderNo, state);
            TradeWebSocketEnvelope envelope = new TradeWebSocketEnvelope(buyerId, event);
            redisTemplate.convertAndSend(TradeWebSocketEventConfiguration.REDIS_CHANNEL,
                    objectMapper.writeValueAsString(envelope));
        } catch (IOException invalidEvent) {
            throw new IllegalArgumentException("Could not decode commerce event", invalidEvent);
        }
    }

    private static String text(JsonNode payload, String name) {
        JsonNode value = payload == null ? null : payload.get(name);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("Commerce event is missing " + name);
        }
        return value.textValue();
    }

    private static String projectedState(String eventType, JsonNode payload) {
        return switch (eventType) {
            case "OrderCreated" -> "PENDING_PAYMENT";
            case "OrderPaid" -> "PAID";
            case "OrderClosed" -> "CLOSED";
            case "OrderRefunded" -> refundOrderState(text(payload, "orderState"), "REFUNDED", "CLOSED");
            case "OrderRefundFailed" -> refundOrderState(text(payload, "orderState"), "PAID", "CLOSED");
            default -> throw new IllegalStateException("Unreachable commerce event type");
        };
    }

    private static String refundOrderState(String state, String primary, String secondary) {
        if (primary.equals(state) || secondary.equals(state)) {
            return state;
        }
        throw new IllegalArgumentException("Refund event contains an invalid order state");
    }
}

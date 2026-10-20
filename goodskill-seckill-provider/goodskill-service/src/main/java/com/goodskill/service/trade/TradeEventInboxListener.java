package com.goodskill.service.trade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@Profile("production")
public class TradeEventInboxListener {

    private final ObjectMapper objectMapper;
    private final TradeEventInboxService inboxService;

    public TradeEventInboxListener(ObjectMapper objectMapper, TradeEventInboxService inboxService) {
        this.objectMapper = objectMapper;
        this.inboxService = inboxService;
    }

    @RabbitListener(queues = TradeEventRabbitConfiguration.QUEUE)
    public void receive(Message message) {
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        Object eventId = headers.get("eventId");
        Object aggregateType = headers.get("aggregateType");
        Object aggregateId = headers.get("aggregateId");
        Object eventType = headers.get("eventType");
        Object eventVersion = headers.get("eventVersion");
        if (!(eventId instanceof String eventIdValue)
                || !(aggregateType instanceof String aggregateTypeValue)
                || !(aggregateId instanceof String aggregateIdValue)
                || !(eventType instanceof String eventTypeValue)
                || !(eventVersion instanceof Number versionValue)) {
            throw new AmqpRejectAndDontRequeueException("Malformed commerce event metadata");
        }

        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            objectMapper.readTree(payload);
        } catch (JsonProcessingException malformedJson) {
            throw new AmqpRejectAndDontRequeueException("Malformed commerce event payload", malformedJson);
        }
        try {
            inboxService.persist(new TradeEventEnvelope(eventIdValue, aggregateTypeValue, aggregateIdValue,
                    eventTypeValue, versionValue.intValue(), payload));
        } catch (IllegalArgumentException malformedEnvelope) {
            throw new AmqpRejectAndDontRequeueException("Malformed commerce event envelope", malformedEnvelope);
        }
    }
}

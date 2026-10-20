package com.goodskill.service.trade;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Component
@Profile("production")
public class TradeOutboxRabbitPublisher {

    private static final Pattern EVENT_TYPE = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,79}");

    private final RabbitTemplate rabbitTemplate;
    private final long confirmTimeoutMillis;

    public TradeOutboxRabbitPublisher(RabbitTemplate rabbitTemplate,
                                      @Value("${goodskill.trade.outbox-confirm-timeout-ms:5000}")
                                      long confirmTimeoutMillis) {
        if (confirmTimeoutMillis < 100 || confirmTimeoutMillis > 60_000) {
            throw new IllegalArgumentException("Outbox confirm timeout is outside the supported bound");
        }
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeoutMillis = confirmTimeoutMillis;
    }

    public void publish(TradeOutboxEvent event) {
        if (event.eventId() == null || event.eventId().isBlank() || event.payload() == null
                || !EVENT_TYPE.matcher(event.eventType()).matches() || event.eventVersion() < 1) {
            throw new IllegalArgumentException("Invalid commerce outbox event");
        }

        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.eventId());
        properties.setHeader("eventId", event.eventId());
        properties.setHeader("aggregateType", event.aggregateType());
        properties.setHeader("aggregateId", event.aggregateId());
        properties.setHeader("eventType", event.eventType());
        properties.setHeader("eventVersion", event.eventVersion());
        Message message = new Message(event.payload().getBytes(StandardCharsets.UTF_8), properties);

        CorrelationData correlation = new CorrelationData(event.eventId());
        String routingKey = "commerce." + event.eventType().toLowerCase(Locale.ROOT);
        try {
            rabbitTemplate.send(TradeEventRabbitConfiguration.EXCHANGE, routingKey, message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ negatively acknowledged the commerce event");
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ returned the unroutable commerce event");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RabbitMQ confirmation wait was interrupted", interrupted);
        } catch (ExecutionException | TimeoutException unavailable) {
            throw new IllegalStateException("RabbitMQ did not confirm the commerce event", unavailable);
        }
    }
}

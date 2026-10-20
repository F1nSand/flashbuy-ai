package com.goodskill.service.trade;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class TradeOutboxRabbitPublisherTest {

    @Test
    void publishesPersistentMessageAndWaitsForBrokerConfirm() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            CorrelationData correlation = invocation.getArgument(3);
            assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
            assertEquals("event-1", message.getMessageProperties().getMessageId());
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq(TradeEventRabbitConfiguration.EXCHANGE), eq("commerce.ordercreated"),
                any(Message.class), any(CorrelationData.class));

        new TradeOutboxRabbitPublisher(rabbitTemplate, 1000).publish(event());
    }

    @Test
    void rejectsNegativeBrokerConfirm() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker rejected"));
            return null;
        }).when(rabbitTemplate).send(eq(TradeEventRabbitConfiguration.EXCHANGE), eq("commerce.ordercreated"),
                any(Message.class), any(CorrelationData.class));

        assertThrows(IllegalStateException.class,
                () -> new TradeOutboxRabbitPublisher(rabbitTemplate, 1000).publish(event()));
    }

    @Test
    void treatsMissingConfirmAsRetryableFailure() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        doAnswer(invocation -> null)
                .when(rabbitTemplate).send(eq(TradeEventRabbitConfiguration.EXCHANGE), eq("commerce.ordercreated"),
                        any(Message.class), any(CorrelationData.class));

        assertThrows(IllegalStateException.class,
                () -> new TradeOutboxRabbitPublisher(rabbitTemplate, 100).publish(event()));
    }

    private static TradeOutboxEvent event() {
        return new TradeOutboxEvent("event-1", "ORDER", "GS0123456789ABCDEF0123456789ABCDEF",
                "OrderCreated", 1, "{\"orderNo\":\"GS0123456789ABCDEF0123456789ABCDEF\"}", 1);
    }
}

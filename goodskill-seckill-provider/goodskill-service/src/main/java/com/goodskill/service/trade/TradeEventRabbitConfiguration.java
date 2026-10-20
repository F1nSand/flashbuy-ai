package com.goodskill.service.trade;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

import java.util.Map;

@Configuration
@Profile("production")
@EnableRabbit
public class TradeEventRabbitConfiguration {

    public static final String EXCHANGE = "goodskill.commerce.events.v1";
    public static final String QUEUE = "goodskill.commerce.events.inbox.v1";
    public static final String DEAD_EXCHANGE = "goodskill.commerce.events.dead.v1";
    public static final String DEAD_QUEUE = "goodskill.commerce.events.dead-letter.v1";
    public static final String DEAD_ROUTING_KEY = "commerce.events.dead";
    public static final String ROUTING_PATTERN = "commerce.#";

    @Bean
    public Declarables commerceEventTopology() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        DirectExchange deadExchange = new DirectExchange(DEAD_EXCHANGE, true, false);
        Queue queue = new Queue(QUEUE, true, false, false, Map.of(
                "x-message-ttl", 604_800_000,
                "x-max-length", 1_000_000,
                "x-overflow", "reject-publish",
                "x-dead-letter-exchange", DEAD_EXCHANGE,
                "x-dead-letter-routing-key", DEAD_ROUTING_KEY));
        Queue deadQueue = new Queue(DEAD_QUEUE, true, false, false, Map.of(
                "x-message-ttl", 2_592_000_000L,
                "x-max-length", 100_000,
                "x-overflow", "reject-publish"));
        return new Declarables(exchange, deadExchange, queue, deadQueue,
                BindingBuilder.bind(queue).to(exchange).with(ROUTING_PATTERN),
                BindingBuilder.bind(deadQueue).to(deadExchange).with(DEAD_ROUTING_KEY));
    }
}

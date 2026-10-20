package com.goodskill.web.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@Profile("production")
public class TradeRealtimeRedisConfiguration {

    @Bean
    public RedisMessageListenerContainer tradeRealtimeRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            TradeRealtimeRedisSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(TradeWebSocketEventConfiguration.REDIS_CHANNEL));
        return container;
    }
}

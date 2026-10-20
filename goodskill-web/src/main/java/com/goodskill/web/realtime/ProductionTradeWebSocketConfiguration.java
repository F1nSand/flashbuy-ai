package com.goodskill.web.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.Set;

@Configuration
@Profile("production")
@EnableWebSocketMessageBroker
public class ProductionTradeWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    private final Set<String> allowedOrigins;
    private final TradeWebSocketHandshakeInterceptor handshakeInterceptor;
    private final TradeWebSocketHandshakeHandler handshakeHandler;
    private final TradeWebSocketSecurityInterceptor securityInterceptor;

    public ProductionTradeWebSocketConfiguration(
            @Value("${goodskill.websocket.allowed-origins:}") String configuredOrigins,
            TradeWebSocketHandshakeInterceptor handshakeInterceptor,
            TradeWebSocketHandshakeHandler handshakeHandler,
            TradeWebSocketSecurityInterceptor securityInterceptor) {
        this.allowedOrigins = TradeWebSocketOriginPolicy.parse(configuredOrigins);
        this.handshakeInterceptor = handshakeInterceptor;
        this.handshakeHandler = handshakeHandler;
        this.securityInterceptor = securityInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/trades")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new))
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(handshakeHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(securityInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(16 * 1024)
                .setSendBufferSizeLimit(64 * 1024)
                .setSendTimeLimit(10_000);
    }
}

package com.goodskill.web.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class TradeWebSocketSecurityInterceptorTest {

    private final TradeWebSocketSecurityInterceptor interceptor = new TradeWebSocketSecurityInterceptor();
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void registersConnectAndAllowsOnlyTheTradeEventSubscription() {
        assertNotNull(interceptor.preSend(frame(StompCommand.CONNECT, "session-1", "/ws/trades", "buyer-1"), channel));
        StompHeaderAccessor subscribe = accessor(StompCommand.SUBSCRIBE, "session-1",
                "/user/queue/trade-events", "buyer-1");
        subscribe.setSubscriptionId("trade-events-1");
        assertNotNull(interceptor.preSend(message(subscribe), channel));
        StompHeaderAccessor duplicate = accessor(StompCommand.SUBSCRIBE, "session-1",
                "/user/queue/trade-events", "buyer-1");
        duplicate.setSubscriptionId("trade-events-2");
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(message(duplicate), channel));
    }

    @Test
    void rejectsOtherDestinationsAndClientPublishes() {
        interceptor.preSend(frame(StompCommand.CONNECT, "session-2", "/ws/trades", "buyer-2"), channel);
        StompHeaderAccessor badSubscribe = accessor(StompCommand.SUBSCRIBE, "session-2", "/topic/trades", "buyer-2");
        badSubscribe.setSubscriptionId("other-queue");
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(message(badSubscribe), channel));
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(frame(StompCommand.SEND,
                "session-2", "/app/trades", "buyer-2"), channel));
    }

    @Test
    void enforcesPerUserConnectionLimit() {
        for (int index = 1; index <= 3; index++) {
            interceptor.preSend(frame(StompCommand.CONNECT, "session-limit-" + index,
                    "/ws/trades", "buyer-limit"), channel);
        }
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(frame(StompCommand.CONNECT,
                "session-limit-4", "/ws/trades", "buyer-limit"), channel));
    }

    private static Message<byte[]> frame(StompCommand command, String sessionId, String destination, String userId) {
        return message(accessor(command, sessionId, destination, userId));
    }

    private static StompHeaderAccessor accessor(StompCommand command, String sessionId, String destination,
                                                String userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        accessor.setDestination(destination);
        accessor.setUser((Principal) () -> userId);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}

package com.goodskill.web.realtime;

import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.context.event.EventListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@Profile("production")
public class TradeWebSocketSecurityInterceptor implements ChannelInterceptor {

    private static final int MAX_CONNECTIONS_PER_USER = 3;
    private static final int MAX_INBOUND_FRAMES_PER_MINUTE = 30;
    private final Map<String, String> sessions = new HashMap<>();
    private final Map<String, Integer> userConnections = new HashMap<>();
    private final Map<String, FrameWindow> frameWindows = new HashMap<>();
    private final Map<String, Set<String>> subscriptions = new HashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }
        if (command == StompCommand.CONNECT) {
            registerSession(accessor.getSessionId(), accessor.getUser() == null ? null : accessor.getUser().getName());
        } else if (command == StompCommand.DISCONNECT) {
            removeSession(accessor.getSessionId());
        } else if (command == StompCommand.SUBSCRIBE) {
            String userId = requireSessionUser(accessor.getSessionId());
            consumeFrameBudget(userId, message);
            if (!"/user/queue/trade-events".equals(accessor.getDestination())) {
                throw new MessageDeliveryException(message, "Only the caller's trade event queue is available");
            }
            String subscriptionId = accessor.getSubscriptionId();
            if (subscriptionId == null || subscriptionId.isBlank()
                    || !registerSubscription(accessor.getSessionId(), subscriptionId)) {
                throw new MessageDeliveryException(message, "Only one trade event subscription is allowed per session");
            }
        } else if (command == StompCommand.SEND || command == StompCommand.ACK
                || command == StompCommand.NACK || command == StompCommand.BEGIN
                || command == StompCommand.COMMIT || command == StompCommand.ABORT) {
            consumeFrameBudget(requireSessionUser(accessor.getSessionId()), message);
            throw new MessageDeliveryException(message, "Client messages are not accepted on the trade event socket");
        } else if (command == StompCommand.UNSUBSCRIBE) {
            String userId = requireSessionUser(accessor.getSessionId());
            consumeFrameBudget(userId, message);
            if (!removeSubscription(accessor.getSessionId(), accessor.getSubscriptionId())) {
                throw new MessageDeliveryException(message, "Unknown trade event subscription");
            }
        } else {
            consumeFrameBudget(requireSessionUser(accessor.getSessionId()), message);
            throw new MessageDeliveryException(message, "Unsupported command on the trade event socket");
        }
        return message;
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        removeSession(accessor.getSessionId());
    }

    private synchronized void registerSession(String sessionId, String userId) {
        if (sessionId == null || userId == null || userId.isBlank()) {
            throw new MessageDeliveryException("Authenticated WebSocket session is required");
        }
        if (sessions.containsKey(sessionId)) {
            return;
        }
        int active = userConnections.getOrDefault(userId, 0);
        if (active >= MAX_CONNECTIONS_PER_USER) {
            throw new MessageDeliveryException("WebSocket connection limit reached");
        }
        sessions.put(sessionId, userId);
        userConnections.put(userId, active + 1);
    }

    private synchronized void removeSession(String sessionId) {
        String userId = sessions.remove(sessionId);
        subscriptions.remove(sessionId);
        if (userId == null) {
            return;
        }
        int active = userConnections.getOrDefault(userId, 1) - 1;
        if (active <= 0) {
            userConnections.remove(userId);
            frameWindows.remove(userId);
        } else {
            userConnections.put(userId, active);
        }
    }

    private synchronized String requireSessionUser(String sessionId) {
        String userId = sessions.get(sessionId);
        if (userId == null) {
            throw new MessageDeliveryException("Authenticated WebSocket session is required");
        }
        return userId;
    }

    private synchronized void consumeFrameBudget(String userId, Message<?> message) {
        long minute = Instant.now().getEpochSecond() / 60;
        FrameWindow window = frameWindows.get(userId);
        if (window == null || window.minute() != minute) {
            window = new FrameWindow(minute, 0);
        }
        if (window.count() >= MAX_INBOUND_FRAMES_PER_MINUTE) {
            throw new MessageDeliveryException(message, "WebSocket message rate limit exceeded");
        }
        frameWindows.put(userId, new FrameWindow(minute, window.count() + 1));
    }

    private synchronized boolean registerSubscription(String sessionId, String subscriptionId) {
        Set<String> current = subscriptions.computeIfAbsent(sessionId, ignored -> new HashSet<>());
        if (current.contains(subscriptionId) || !current.isEmpty()) {
            return false;
        }
        current.add(subscriptionId);
        return true;
    }

    private synchronized boolean removeSubscription(String sessionId, String subscriptionId) {
        Set<String> current = subscriptions.get(sessionId);
        if (current == null || subscriptionId == null) {
            return false;
        }
        boolean removed = current.remove(subscriptionId);
        if (current.isEmpty()) {
            subscriptions.remove(sessionId);
        }
        return removed;
    }

    private record FrameWindow(long minute, int count) {
    }
}

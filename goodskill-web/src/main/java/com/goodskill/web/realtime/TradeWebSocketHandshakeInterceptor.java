package com.goodskill.web.realtime;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@Profile("production")
public class TradeWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private static final Pattern USER_ID = Pattern.compile("[A-Za-z0-9._:@+-]{1,64}");
    private final Set<String> allowedOrigins;

    public TradeWebSocketHandshakeInterceptor(
            @org.springframework.beans.factory.annotation.Value("${goodskill.websocket.allowed-origins:}")
            String configuredOrigins) {
        this.allowedOrigins = TradeWebSocketOriginPolicy.parse(configuredOrigins);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String origin = request.getHeaders().getOrigin();
        String userId = request.getHeaders().getFirst("X-User-Id");
        String normalizedOrigin = null;
        try {
            if (origin != null) {
                normalizedOrigin = TradeWebSocketOriginPolicy.canonicalOrigin(origin);
            }
        } catch (IllegalStateException invalidOrigin) {
            // Reject malformed origins without exposing parser details to callers.
        }
        if (normalizedOrigin == null || !allowedOrigins.contains(normalizedOrigin)
                || userId == null || !USER_ID.matcher(userId).matches()) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put("authenticatedUserId", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No token, URL or request header is written to logs.
    }
}

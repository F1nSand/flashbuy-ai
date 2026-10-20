package com.goodskill.gateway.security;

import com.goodskill.core.enums.CommonConstants;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionRateLimitConfigurationTest {

    private final ProductionRateLimitConfiguration configuration = new ProductionRateLimitConfiguration();

    @Test
    void resolverUsesHashedAuthenticatedUserIdentity() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .header(CommonConstants.USER_ID_HEADER, "buyer-42")
                .build());
        KeyResolver resolver = configuration.commerceIdentityKeyResolver();

        String key = resolver.resolve(exchange).block();

        assertTrue(key.startsWith("buyer:"));
        assertFalse(key.contains("buyer-42"));
        assertEquals(key, resolver.resolve(exchange).block());
    }

    @Test
    void resolverFallsBackToHashedRemoteAddress() {
        String address = "192.0.2.10";
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress(address, 1234))
                .build());

        String key = configuration.commerceIdentityKeyResolver().resolve(exchange).block();

        assertTrue(key.startsWith("ip:"));
        assertFalse(key.contains(address));
    }

    @Test
    void resolverReturnsEmptyWhenNeitherAuthenticatedIdentityNorAddressExists() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());

        assertNull(configuration.commerceIdentityKeyResolver().resolve(exchange).block());
    }

    @Test
    void limiterRejectsUnsafeConfigurationBounds() {
        assertThrows(IllegalArgumentException.class, () -> configuration.tradeOrderRateLimiter(0, 10));
        assertThrows(IllegalArgumentException.class, () -> configuration.tradeOrderRateLimiter(10, 9));
        assertThrows(IllegalArgumentException.class, () -> configuration.tradeOrderRateLimiter(1001, 1001));
        assertThrows(IllegalArgumentException.class, () -> configuration.tradeOrderRateLimiter(1, 10001));
    }

    @Test
    void limiterAcceptsSupportedConfiguration() {
        RedisRateLimiter limiter = configuration.tradeOrderRateLimiter(2, 10);

        assertNotNull(limiter);
    }
}

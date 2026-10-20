package com.goodskill.gateway.security;

import com.goodskill.core.enums.CommonConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Configuration
@Profile("production")
public class ProductionRateLimitConfiguration {

    @Bean
    @Primary
    public FailClosedRedisRateLimiter tradeOrderRateLimiter(
            @Value("${goodskill.gateway.order-rate-per-second:2}") int replenishRate,
            @Value("${goodskill.gateway.order-rate-burst:10}") int burstCapacity) {
        if (replenishRate < 1 || replenishRate > 1000 || burstCapacity < replenishRate || burstCapacity > 10_000) {
            throw new IllegalArgumentException("Production order rate limit is outside the supported bound");
        }
        return new FailClosedRedisRateLimiter(replenishRate, burstCapacity);
    }

    @Bean
    @Primary
    public KeyResolver commerceIdentityKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(CommonConstants.USER_ID_HEADER);
            if (StringUtils.hasText(userId)) {
                return Mono.just("buyer:" + digest(userId));
            }
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress == null) {
                return Mono.empty();
            }
            String clientAddress = remoteAddress.getAddress() == null
                    ? remoteAddress.getHostString() : remoteAddress.getAddress().getHostAddress();
            if (!StringUtils.hasText(clientAddress)) {
                return Mono.empty();
            }
            return Mono.just("ip:" + digest(clientAddress));
        };
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }
}

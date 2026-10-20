package com.goodskill.gateway.security;

import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;

/**
 * Spring Cloud Gateway's Redis limiter fails open on Redis script errors.
 * Order creation must remain blocked when the shared limiter cannot decide.
 */
public final class FailClosedRedisRateLimiter extends RedisRateLimiter {

    public FailClosedRedisRateLimiter(int replenishRate, int burstCapacity) {
        super(replenishRate, burstCapacity);
        setIncludeHeaders(true);
    }

    @Override
    public Mono<RateLimiter.Response> isAllowed(String routeId, String id) {
        return super.isAllowed(routeId, id).map(FailClosedRedisRateLimiter::denyOnRedisFailure);
    }

    static RateLimiter.Response denyOnRedisFailure(RateLimiter.Response response) {
        if (response.isAllowed()
                && "-1".equals(response.getHeaders().get(RedisRateLimiter.REMAINING_HEADER))) {
            return new RateLimiter.Response(false, response.getHeaders());
        }
        return response;
    }
}

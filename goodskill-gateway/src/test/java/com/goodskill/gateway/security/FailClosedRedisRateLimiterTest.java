package com.goodskill.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailClosedRedisRateLimiterTest {

    @Test
    void redisFailureSentinelIsDenied() {
        RateLimiter.Response redisFailure = new RateLimiter.Response(true,
                Map.of(RedisRateLimiter.REMAINING_HEADER, "-1"));

        RateLimiter.Response result = FailClosedRedisRateLimiter.denyOnRedisFailure(redisFailure);

        assertFalse(result.isAllowed());
        assertEquals(redisFailure.getHeaders(), result.getHeaders());
    }

    @Test
    void ordinaryAllowanceAndRateLimitDenialArePreserved() {
        RateLimiter.Response allowed = new RateLimiter.Response(true,
                Map.of(RedisRateLimiter.REMAINING_HEADER, "9"));
        RateLimiter.Response denied = new RateLimiter.Response(false,
                Map.of(RedisRateLimiter.REMAINING_HEADER, "0"));

        assertTrue(FailClosedRedisRateLimiter.denyOnRedisFailure(allowed).isAllowed());
        assertFalse(FailClosedRedisRateLimiter.denyOnRedisFailure(denied).isAllowed());
    }
}

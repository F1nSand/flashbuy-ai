package com.goodskill.gateway.security;

import cn.dev33.satoken.stp.StpUtil;
import com.goodskill.core.enums.CommonConstants;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

class RemoveUntrustedUserHeaderFilterTest {

    @Test
    void removesCallerSuppliedUserIdBeforeForwarding() {
        var request = MockServerHttpRequest.get("/api/seckill/goods/list")
                .header(CommonConstants.USER_ID_HEADER, "forged-user")
                .build();
        var exchange = MockServerWebExchange.from(request);
        var forwardedExchange = new AtomicReference<ServerWebExchange>();
        GatewayFilterChain chain = next -> {
            forwardedExchange.set(next);
            return Mono.empty();
        };

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdDefaultNull).thenReturn(null);
            new RemoveUntrustedUserHeaderFilter().filter(exchange, chain).block();
        }

        assertNull(forwardedExchange.get().getRequest().getHeaders()
                .getFirst(CommonConstants.USER_ID_HEADER));
    }

    @Test
    void replacesCallerSuppliedUserIdWithAuthenticatedLoginId() {
        var request = MockServerHttpRequest.get("/api/auth/user/info")
                .header(CommonConstants.USER_ID_HEADER, "forged-user")
                .build();
        var exchange = MockServerWebExchange.from(request);
        var forwardedExchange = new AtomicReference<ServerWebExchange>();
        GatewayFilterChain chain = next -> {
            forwardedExchange.set(next);
            return Mono.empty();
        };

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdDefaultNull).thenReturn(42);
            new RemoveUntrustedUserHeaderFilter().filter(exchange, chain).block();
        }

        assertEquals("42", forwardedExchange.get().getRequest().getHeaders()
                .getFirst(CommonConstants.USER_ID_HEADER));
    }
}

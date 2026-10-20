package com.goodskill.gateway.security;

import cn.dev33.satoken.stp.StpUtil;
import com.goodskill.core.enums.CommonConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * A caller-supplied identity header is never trusted at the gateway boundary.
 */
@Component
public class RemoveUntrustedUserHeaderFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(CommonConstants.USER_ID_HEADER);
                    Object loginId = StpUtil.getLoginIdDefaultNull();
                    if (loginId != null) {
                        headers.set(CommonConstants.USER_ID_HEADER, loginId.toString());
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(sanitizedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

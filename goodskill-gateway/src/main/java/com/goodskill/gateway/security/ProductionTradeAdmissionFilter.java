package com.goodskill.gateway.security;

import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
@Profile("production")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ProductionTradeAdmissionFilter implements GlobalFilter {

    private static final Pattern ORDER_CREATE = Pattern.compile("/api/seckill/activities/[1-9][0-9]*/orders");
    private static final byte[] PAUSED_RESPONSE =
            "{\"code\":503,\"msg\":\"ADMISSION_PAUSED\",\"data\":null}".getBytes(StandardCharsets.UTF_8);

    private final boolean admissionEnabled;

    public ProductionTradeAdmissionFilter(
            @org.springframework.beans.factory.annotation.Value("${goodskill.trade.admission-enabled:false}")
            boolean admissionEnabled) {
        this.admissionEnabled = admissionEnabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (admissionEnabled || exchange.getRequest().getMethod() != HttpMethod.POST
                || !ORDER_CREATE.matcher(exchange.getRequest().getPath().value()).matches()) {
            return chain.filter(exchange);
        }

        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", "30");
        return response.writeWith(Mono.fromSupplier(() -> response.bufferFactory().wrap(PAUSED_RESPONSE)));
    }
}

package com.goodskill.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductionTradeAdmissionFilterTest {

    private static final String ORDER_PATH = "/api/seckill/activities/42/orders";

    @Test
    void closedAdmissionRejectsOrderCreationWithRetryableBusinessResponse() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(ORDER_PATH).build());

        new ProductionTradeAdmissionFilter(false).filter(exchange, chain).block();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertEquals("30", exchange.getResponse().getHeaders().getFirst("Retry-After"));
        assertTrue(exchange.getResponse().getBodyAsString().block().contains("ADMISSION_PAUSED"));
        verifyNoInteractions(chain);
    }

    @Test
    void enabledAdmissionAndNonOrderRoutesContinue() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
        MockServerWebExchange orderExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post(ORDER_PATH).build());
        MockServerWebExchange readExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/order/me").build());

        ProductionTradeAdmissionFilter enabled = new ProductionTradeAdmissionFilter(true);
        enabled.filter(orderExchange, chain).block();
        new ProductionTradeAdmissionFilter(false).filter(readExchange, chain).block();

        verify(chain).filter(orderExchange);
        verify(chain).filter(readExchange);
    }
}

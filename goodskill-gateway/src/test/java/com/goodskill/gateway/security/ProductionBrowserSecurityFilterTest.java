package com.goodskill.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionBrowserSecurityFilterTest {

    private final ProductionBrowserSecurityFilter filter =
            new ProductionBrowserSecurityFilter("https://shop.example.com");

    @Test
    void rejectsCookieAuthenticatedMutationWithoutDoubleSubmitToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/order/me/GS0123456789ABCDEF0123456789ABCDEF/cancel")
                .header(HttpHeaders.ORIGIN, "https://shop.example.com"));
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertFalse(forwarded.get());
    }

    @Test
    void acceptsMatchingCsrfCookieAndHeaderForAllowedOrigin() {
        String token = "A".repeat(43);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/order/me/GS0123456789ABCDEF0123456789ABCDEF/cancel")
                .header(HttpHeaders.ORIGIN, "https://shop.example.com")
                .header(ProductionBrowserSecurityFilter.CSRF_HEADER, token)
                .cookie(new HttpCookie(ProductionBrowserSecurityFilter.CSRF_COOKIE, token)));
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertTrue(forwarded.get());
    }

    @Test
    void loginRequiresAnAllowedOriginAndIssuesReadableCsrfCookie() {
        MockServerWebExchange rejected = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/auth/login").header(HttpHeaders.ORIGIN, "https://attacker.example"));
        filter.filter(rejected, ignored -> Mono.empty()).block();
        assertEquals(HttpStatus.FORBIDDEN, rejected.getResponse().getStatusCode());

        MockServerWebExchange accepted = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/auth/login").header(HttpHeaders.ORIGIN, "https://shop.example.com"));
        filter.filter(accepted, ignored -> Mono.empty()).block();
        accepted.getResponse().setComplete().block();
        String setCookie = accepted.getResponse().getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie.startsWith(ProductionBrowserSecurityFilter.CSRF_COOKIE + "="));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=Strict"));
        assertFalse(setCookie.contains("HttpOnly"));
    }

    @Test
    void signedProviderNotificationDoesNotDependOnBrowserCookies() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/payment/alipay/notify"));
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertTrue(forwarded.get());
    }
}

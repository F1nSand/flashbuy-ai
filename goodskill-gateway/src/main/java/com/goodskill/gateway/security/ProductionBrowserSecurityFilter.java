package com.goodskill.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

/** Enforces exact browser origins and a host-only double-submit token for cookie-authenticated writes. */
@Component
@Profile("production")
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class ProductionBrowserSecurityFilter implements GlobalFilter {

    static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";
    static final String CSRF_HEADER = "X-CSRF-TOKEN";
    private static final String LOGIN = "/api/auth/login";
    private static final String REGISTER = "/api/auth/register";
    private static final String LOGOUT = "/api/auth/logout";
    private static final String PROVIDER_NOTIFY = "/api/payment/alipay/notify";
    private static final byte[] REJECTED =
            "{\"code\":403,\"msg\":\"FORBIDDEN\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Set<String> allowedOrigins;

    public ProductionBrowserSecurityFilter(
            @org.springframework.beans.factory.annotation.Value("${goodskill.security.browser-origins:}")
            String configuredOrigins) {
        this.allowedOrigins = BrowserOriginPolicy.parse(configuredOrigins);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getPath().value();
        if (method == null || isSafeMethod(method) || (method == HttpMethod.POST && PROVIDER_NOTIFY.equals(path))) {
            return chain.filter(exchange);
        }
        if (!hasAllowedOrigin(exchange)) {
            return reject(exchange);
        }
        if (method == HttpMethod.POST && (LOGIN.equals(path) || REGISTER.equals(path))) {
            if (LOGIN.equals(path)) {
                issueCsrfCookie(exchange);
            }
            return chain.filter(exchange);
        }
        if (!hasValidCsrfToken(exchange)) {
            return reject(exchange);
        }
        if (method == HttpMethod.POST && LOGOUT.equals(path)) {
            clearCsrfCookie(exchange);
        }
        return chain.filter(exchange);
    }

    private boolean hasAllowedOrigin(ServerWebExchange exchange) {
        String origin = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (origin == null) {
            return false;
        }
        try {
            return allowedOrigins.contains(BrowserOriginPolicy.canonicalOrigin(origin));
        } catch (IllegalStateException invalidOrigin) {
            return false;
        }
    }

    private static boolean hasValidCsrfToken(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(CSRF_COOKIE);
        String header = exchange.getRequest().getHeaders().getFirst(CSRF_HEADER);
        if (cookie == null || header == null || cookie.getValue().length() < 32 || header.length() < 32) {
            return false;
        }
        return MessageDigest.isEqual(cookie.getValue().getBytes(StandardCharsets.UTF_8),
                header.getBytes(StandardCharsets.UTF_8));
    }

    private static void issueCsrfCookie(ServerWebExchange exchange) {
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        ResponseCookie cookie = ResponseCookie.from(CSRF_COOKIE, token)
                .secure(true).httpOnly(false).sameSite("Strict").path("/")
                .maxAge(Duration.ofDays(7)).build();
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().addCookie(cookie);
            return Mono.empty();
        });
    }

    private static void clearCsrfCookie(ServerWebExchange exchange) {
        ResponseCookie cookie = ResponseCookie.from(CSRF_COOKIE, "")
                .secure(true).httpOnly(false).sameSite("Strict").path("/")
                .maxAge(Duration.ZERO).build();
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().addCookie(cookie);
            return Mono.empty();
        });
    }

    private static Mono<Void> reject(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(REJECTED)));
    }

    private static boolean isSafeMethod(HttpMethod method) {
        return method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS;
    }
}

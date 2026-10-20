package com.goodskill.web.realtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TradeWebSocketOriginPolicyTest {

    @Test
    void acceptsOnlyNormalizedExactHttpsOrigins() {
        assertEquals("https://shop.example.com", TradeWebSocketOriginPolicy
                .parse(" HTTPS://SHOP.EXAMPLE.COM:443,https://admin.example.com:8443 ").stream()
                .filter(origin -> origin.equals("https://shop.example.com"))
                .findFirst().orElseThrow());
        assertEquals("https://admin.example.com:8443", TradeWebSocketOriginPolicy
                .canonicalOrigin("https://admin.example.com:8443"));
    }

    @Test
    void rejectsUntrustedOrNonOriginUris() {
        assertThrows(IllegalStateException.class, () -> TradeWebSocketOriginPolicy.parse(""));
        assertThrows(IllegalStateException.class,
                () -> TradeWebSocketOriginPolicy.parse("http://shop.example.com"));
        assertThrows(IllegalStateException.class,
                () -> TradeWebSocketOriginPolicy.parse("https://shop.example.com/path"));
        assertThrows(IllegalStateException.class,
                () -> TradeWebSocketOriginPolicy.parse("https://user@shop.example.com"));
    }
}

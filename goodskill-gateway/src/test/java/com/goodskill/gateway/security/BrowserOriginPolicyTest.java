package com.goodskill.gateway.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrowserOriginPolicyTest {

    @Test
    void canonicalizesExactHttpsOrigins() {
        assertEquals("https://shop.example.com", BrowserOriginPolicy.canonicalOrigin("HTTPS://SHOP.EXAMPLE.COM:443"));
        assertEquals("https://shop.example.com:8443", BrowserOriginPolicy
                .canonicalOrigin("https://shop.example.com:8443"));
    }

    @Test
    void rejectsNonHttpsAndNonOriginValues() {
        assertThrows(IllegalStateException.class, () -> BrowserOriginPolicy.parse(""));
        assertThrows(IllegalStateException.class,
                () -> BrowserOriginPolicy.parse("http://shop.example.com"));
        assertThrows(IllegalStateException.class,
                () -> BrowserOriginPolicy.parse("https://shop.example.com/path"));
    }
}

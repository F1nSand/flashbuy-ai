package com.goodskill.gateway.security;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class BrowserOriginPolicy {

    private BrowserOriginPolicy() {
    }

    static Set<String> parse(String configuredOrigins) {
        if (configuredOrigins == null || configuredOrigins.isBlank()) {
            throw new IllegalStateException("GOODSKILL_WEB_ALLOWED_ORIGINS must list trusted HTTPS browser origins");
        }
        Set<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(BrowserOriginPolicy::canonicalOrigin)
                .collect(Collectors.toUnmodifiableSet());
        if (origins.isEmpty()) {
            throw new IllegalStateException("GOODSKILL_WEB_ALLOWED_ORIGINS must not be empty");
        }
        return origins;
    }

    static String canonicalOrigin(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty())) {
                throw new IllegalStateException("Only exact HTTPS browser origins are accepted");
            }
            int port = uri.getPort() == 443 ? -1 : uri.getPort();
            return "https://" + uri.getHost().toLowerCase(Locale.ROOT) + (port < 0 ? "" : ":" + port);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("GOODSKILL_WEB_ALLOWED_ORIGINS contains an invalid origin", invalid);
        }
    }
}

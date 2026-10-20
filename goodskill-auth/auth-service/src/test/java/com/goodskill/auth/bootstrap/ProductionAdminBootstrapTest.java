package com.goodskill.auth.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionAdminBootstrapTest {

    @Test
    void acceptsStrongBcryptCompatibleBootstrapCredentials() {
        assertDoesNotThrow(() -> ProductionAdminBootstrap.validateCredentials("root-operator", "long-secret-passphrase"));
        assertDoesNotThrow(() -> ProductionAdminBootstrap.validateCredentials("管理员", "安".repeat(24)));
    }

    @Test
    void rejectsShortOrBcryptTruncatedPasswords() {
        assertThrows(IllegalStateException.class,
                () -> ProductionAdminBootstrap.validateCredentials("root-operator", "short-pass"));
        assertThrows(IllegalStateException.class,
                () -> ProductionAdminBootstrap.validateCredentials("root-operator", "安".repeat(25)));
    }

    @Test
    void rejectsMissingAndOversizedUsernames() {
        assertThrows(IllegalStateException.class,
                () -> ProductionAdminBootstrap.validateCredentials(" ", "long-secret-passphrase"));
        assertThrows(IllegalStateException.class,
                () -> ProductionAdminBootstrap.validateCredentials("a".repeat(121), "long-secret-passphrase"));
    }
}

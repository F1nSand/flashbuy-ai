package com.goodskill.service.trade;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TradePaymentConfigurationTest {

    @Test
    void missingPaymentSwitchDefaultsToDisabledWithoutMerchantSettings() {
        TradePaymentProvider provider = new TradePaymentConfiguration()
                .tradePaymentProvider(new MockEnvironment());

        assertEquals(false, provider.enabled());
    }

    @Test
    void enabledPaymentFailsStartupWhenMerchantSettingsAreMissing() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("goodskill.payment.enabled", "true");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new TradePaymentConfiguration().tradePaymentProvider(environment));

        assertEquals("Missing required payment setting: goodskill.payment.app-id", error.getMessage());
    }

    @Test
    void enabledPaymentRejectsInsecureCallbackUrlsBeforeLoadingCertificates() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("goodskill.payment.enabled", "true")
                .withProperty("goodskill.payment.app-id", "app-id")
                .withProperty("goodskill.payment.seller-id", "seller-id")
                .withProperty("goodskill.payment.private-key", "private-key")
                .withProperty("goodskill.payment.app-certificate", "unused-app-cert")
                .withProperty("goodskill.payment.alipay-certificate", "unused-alipay-cert")
                .withProperty("goodskill.payment.root-certificate", "unused-root-cert")
                .withProperty("goodskill.payment.notify-url", "http://merchant.example/notify")
                .withProperty("goodskill.payment.return-url", "https://merchant.example/return");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new TradePaymentConfiguration().tradePaymentProvider(environment));

        assertEquals("Enabled payment configuration has an invalid gateway, URL, or mode", error.getMessage());
    }
}

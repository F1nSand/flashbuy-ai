package com.goodskill.service.trade;

import com.alipay.api.AlipayApiException;
import com.alipay.api.CertAlipayRequest;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.Set;

@Configuration
@Profile("production")
public class TradePaymentConfiguration {

    private static final Set<String> ALLOWED_GATEWAYS = Set.of(
            "https://openapi.alipay.com/gateway.do",
            "https://openapi-sandbox.dl.alipaydev.com/gateway.do");

    @Bean
    public TradePaymentProvider tradePaymentProvider(Environment environment) {
        if (!environment.getProperty("goodskill.payment.enabled", Boolean.class, false)) {
            return new DisabledTradePaymentProvider();
        }
        String appId = required(environment, "goodskill.payment.app-id");
        String sellerId = required(environment, "goodskill.payment.seller-id");
        String privateKey = required(environment, "goodskill.payment.private-key");
        String appCertificate = required(environment, "goodskill.payment.app-certificate");
        String alipayCertificate = required(environment, "goodskill.payment.alipay-certificate");
        String rootCertificate = required(environment, "goodskill.payment.root-certificate");
        String notifyUrl = required(environment, "goodskill.payment.notify-url");
        String returnUrl = required(environment, "goodskill.payment.return-url");
        String gateway = environment.getProperty("goodskill.payment.gateway",
                "https://openapi.alipay.com/gateway.do");
        String mode = environment.getProperty("goodskill.payment.checkout-mode", "page");
        if (!ALLOWED_GATEWAYS.contains(gateway) || !isHttpsUrl(notifyUrl) || !isHttpsUrl(returnUrl)
                || !("page".equals(mode) || "wap".equals(mode))) {
            throw new IllegalStateException("Enabled payment configuration has an invalid gateway, URL, or mode");
        }
        requireFile(appCertificate);
        requireFile(alipayCertificate);
        requireFile(rootCertificate);

        CertAlipayRequest cert = new CertAlipayRequest();
        cert.setServerUrl(gateway);
        cert.setAppId(appId);
        cert.setPrivateKey(privateKey);
        cert.setCharset("UTF-8");
        cert.setFormat("json");
        cert.setSignType("RSA2");
        cert.setCertPath(appCertificate);
        cert.setAlipayPublicCertPath(alipayCertificate);
        cert.setRootCertPath(rootCertificate);
        try {
            return new AlipayTradePaymentProvider(new DefaultAlipayClient(cert), appId, sellerId,
                    notifyUrl, returnUrl, mode, alipayCertificate);
        } catch (AlipayApiException error) {
            throw new IllegalStateException("Could not initialize the Alipay certificate client", error);
        }
    }

    private static String required(Environment environment, String property) {
        String value = environment.getProperty(property);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Missing required payment setting: " + property);
        }
        return value.trim();
    }

    private static void requireFile(String value) {
        if (!Files.isRegularFile(Path.of(value)) || !Files.isReadable(Path.of(value))) {
            throw new IllegalStateException("Configured Alipay certificate file is missing or unreadable");
        }
    }

    private static boolean isHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && StringUtils.hasText(uri.getHost())
                    && uri.getUserInfo() == null && uri.getFragment() == null && value.length() <= 2048;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}

package com.goodskill.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionRouteAccessPolicyTest {

    private final ProductionRouteAccessPolicy policy = new ProductionRouteAccessPolicy();

    @Test
    void onlyLoginAndRegistrationAreAnonymous() {
        assertTrue(policy.isAnonymousAllowed(HttpMethod.POST, "/api/auth/login"));
        assertTrue(policy.isAnonymousAllowed(HttpMethod.POST, "/api/auth/register"));
        assertTrue(policy.isAnonymousAllowed(HttpMethod.POST, "/api/payment/alipay/notify"));
        assertFalse(policy.isAnonymousAllowed(HttpMethod.GET, "/api/payment/alipay/notify"));
        assertFalse(policy.isAnonymousAllowed(HttpMethod.GET, "/api/seckill/goods/list"));
        assertFalse(policy.isAnonymousAllowed(HttpMethod.GET, "/api/seckill/seckill/1000/detail"));
        assertFalse(policy.isAnonymousAllowed(HttpMethod.GET, "/api/auth/user/list"));
        assertFalse(policy.isAnonymousAllowed(HttpMethod.POST, "/api/seckill/goods"));
        assertTrue(policy.isAnonymousAllowed(HttpMethod.GET,
                "/api/seckill/files/public/0123456789abcdef0123456789abcdef"));
        assertFalse(policy.isAnonymousAllowed(HttpMethod.GET,
                "/api/seckill/files/0123456789abcdef0123456789abcdef"));
    }

    @Test
    void productionDisablesSimulationAiAndExternalPermissionLookup() {
        assertTrue(policy.isDisabled("/api/web/synchronized"));
        assertTrue(policy.isDisabled("/goodskill-web/synchronized"));
        assertFalse(policy.isDisabled("/api/ai/assistant/chat"));
        assertTrue(policy.isDisabled("/api/auth/user/listUserPermission"));
        assertTrue(policy.isDisabled("/api/seckill/goods/list"));
        assertTrue(policy.isDisabled("/api/seckill/seckill/1000/detail"));
        assertTrue(policy.isDisabled("/api/order/saveRecord"));
        assertFalse(policy.isDisabled("/api/order/me/GS0123456789ABCDEF0123456789ABCDEF/payment"));
        assertTrue(policy.isDisabled("/api/common/oss/files/upload"));
        assertTrue(policy.isDisabled("/api/seata/branch/commit"));
        assertTrue(policy.isDisabled("/goodskill-auth/login"));
        assertTrue(policy.isDisabled("/goodskill-service-provider/seckill/list"));
    }

    @Test
    void managementRoutesRequireTheirAssignedRoles() {
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/auth/user/list")
                .orElseThrow().contains("system-manager"));
        assertTrue(policy.requiredAnyRole(HttpMethod.POST, "/api/seckill/goods")
                .orElseThrow().contains("goods-manager"));
        assertTrue(policy.requiredAnyRole(HttpMethod.DELETE, "/api/seckill/seckill/1000")
                .orElseThrow().contains("seckill-manager"));
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/auth/user/info").orElseThrow().isEmpty());
        assertTrue(policy.requiredAnyRole(HttpMethod.POST, "/api/ai/assistant/chat").orElseThrow().isEmpty());
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/unknown/internal").isEmpty());
    }

    @Test
    void onlyTheProductionOrderCreateRouteIsEnabledForBuyers() {
        assertTrue(policy.requiredAnyRole(HttpMethod.POST, "/api/seckill/activities/42/orders")
                .orElseThrow().contains("buyer"));
        assertTrue(policy.isAnonymousAllowed(HttpMethod.GET, "/api/seckill/activities"));
        assertTrue(policy.isAnonymousAllowed(HttpMethod.GET, "/api/seckill/activities/42"));
        assertTrue(policy.requiredAnyRole(HttpMethod.POST, "/api/seckill/activities")
                .orElseThrow().contains("seckill-manager"));
        assertTrue(policy.requiredAnyRole(HttpMethod.POST, "/api/seckill/activities/42/publish")
                .orElseThrow().contains("system-manager"));
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/seckill/activities/manage/42")
                .orElseThrow().contains("seckill-manager"));
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/seckill/activities/manage")
                .orElseThrow().contains("seckill-manager"));
        assertFalse(policy.isAnonymousAllowed(HttpMethod.GET, "/api/seckill/activities/42/orders"));
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/seckill/activities/42/orders").isEmpty());
        assertTrue(policy.requiredAnyRole(HttpMethod.POST, "/api/seckill/activities/0/orders").isEmpty());
        assertFalse(policy.isDisabled("/api/seckill/activities/42/orders"));
        assertTrue(policy.isDisabled("/api/seckill/seckill/42/exposer"));
    }

    @Test
    void onlyExactBuyerOrderReadAndCancelRoutesAreEnabled() {
        String orderNo = "GS0123456789ABCDEF0123456789ABCDEF";
        assertFalse(policy.isDisabled("/api/order/me"));
        assertFalse(policy.isDisabled("/api/order/me/" + orderNo));
        assertFalse(policy.isDisabled("/api/order/me/" + orderNo + "/cancel"));
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/order/me").orElseThrow().contains("buyer"));
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/order/me/" + orderNo)
                .orElseThrow().contains("buyer"));
        assertTrue(policy.requiredAnyRole(HttpMethod.POST, "/api/order/me/" + orderNo + "/cancel")
                .orElseThrow().contains("buyer"));
        assertTrue(policy.requiredAnyRole(HttpMethod.POST, "/api/order/me/" + orderNo + "/payment")
                .orElseThrow().contains("buyer"));
        assertFalse(policy.isDisabled("/api/order/me/" + orderNo + "/payment"));
        assertTrue(policy.requiredAnyRole(HttpMethod.POST, "/api/order/manage/" + orderNo + "/refund")
                .orElseThrow().contains("system-manager"));
        assertFalse(policy.isDisabled("/api/order/manage/" + orderNo + "/refund"));
        assertTrue(policy.requiredAnyRole(HttpMethod.POST, "/api/order/me/" + orderNo).isEmpty());
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/order/me/" + orderNo + "/cancel").isEmpty());
        assertTrue(policy.requiredAnyRole(HttpMethod.DELETE, "/api/order/me").isEmpty());
        assertTrue(policy.requiredAnyRole(HttpMethod.POST, "/api/order/me/" + orderNo + "/payment/anything").isEmpty());
        assertTrue(policy.isDisabled("/api/order/me/not-an-order-number"));
        assertTrue(policy.isDisabled("/api/order/me/" + orderNo + "/cancel/anything"));
    }

    @Test
    void productionFileAndAuditRoutesAreNarrowlyEnabled() {
        String fileId = "0123456789abcdef0123456789abcdef";
        assertFalse(policy.isDisabled("/api/seckill/files/product-images"));
        assertTrue(policy.requiredAnyRole(HttpMethod.POST, "/api/seckill/files/product-images")
                .orElseThrow().contains("goods-manager"));
        assertTrue(policy.requiredAnyRole(HttpMethod.DELETE, "/api/seckill/files/" + fileId)
                .orElseThrow().contains("system-manager"));
        assertFalse(policy.isDisabled("/api/seckill/files/public/" + fileId));
        assertTrue(policy.isDisabled("/api/seckill/files/public/not-an-id"));
        assertTrue(policy.isDisabled("/api/common/oss/files/upload"));
        assertFalse(policy.isDisabled("/api/order/manage/audit"));
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/order/manage/audit")
                .orElseThrow().contains("system-manager"));
        assertTrue(policy.isDisabled("/api/order/manage/audit/export"));
        String orderNo = "GS0123456789ABCDEF0123456789ABCDEF";
        assertFalse(policy.isDisabled("/api/order/manage/orders"));
        assertFalse(policy.isDisabled("/api/order/manage/orders/" + orderNo));
        assertFalse(policy.isDisabled("/api/order/manage/orders/" + orderNo + "/shipment"));
        assertFalse(policy.isDisabled("/api/order/manage/inventory"));
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/order/manage/orders")
                .orElseThrow().contains("system-manager"));
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/order/manage/orders/" + orderNo)
                .orElseThrow().contains("system-manager"));
        assertTrue(policy.requiredAnyRole(HttpMethod.POST, "/api/order/manage/orders/" + orderNo + "/shipment")
                .orElseThrow().contains("system-manager"));
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/order/manage/inventory")
                .orElseThrow().contains("system-manager"));
        assertFalse(policy.isDisabled("/api/order/manage/metrics"));
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/order/manage/metrics")
                .orElseThrow().contains("system-manager"));
        assertTrue(policy.isDisabled("/api/order/manage/orders/not-an-order-number"));
    }

    @Test
    void onlyTheAuthenticatedTradeWebSocketHandshakeIsEnabled() {
        assertFalse(policy.isDisabled("/api/web/ws/trades"));
        assertTrue(policy.requiredAnyRole(HttpMethod.GET, "/api/web/ws/trades")
                .orElseThrow().contains("buyer"));
        assertTrue(policy.isDisabled("/api/web/ws/other"));
        assertTrue(policy.isDisabled("/api/web/ws/trades/"));
        assertTrue(policy.requiredAnyRole(HttpMethod.POST, "/api/web/ws/trades").isEmpty());
    }
}

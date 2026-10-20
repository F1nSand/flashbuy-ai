package com.goodskill.gateway.security;

import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Narrow public routes and role requirements applied by the gateway in production.
 */
public final class ProductionRouteAccessPolicy {

    private static final List<String> DISABLED_PREFIXES = List.of(
            // These are legacy demo controllers and are not backed by the production trade schema.
            "/api/web", "/goodskill-web", "/goodskill-ai",
            "/goodskill-auth", "/goodskill-order", "/goodskill-seckill",
            "/goodskill-service-provider", "/goodskill-common", "/goodskill-seata",
            "/api/seckill/goods", "/api/seckill/seckill", "/api/seckill/files", "/api/order",
            "/api/common", "/api/seata", "/goodskill-seata"
    );

    private static final List<String> AUTHORIZATION_LOOKUP_PATHS = List.of(
            "/api/auth/user/listUserPermission", "/api/auth/user/listUserRole"
    );
    private static final Pattern TRADE_ORDER_CREATE =
            Pattern.compile("^/api/seckill/activities/[1-9][0-9]*/orders$");
    private static final Pattern ACTIVITY_DETAIL =
            Pattern.compile("^/api/seckill/activities/[1-9][0-9]*$");
    private static final Pattern ACTIVITY_UPDATE =
            Pattern.compile("^/api/seckill/activities/[1-9][0-9]*$");
    private static final Pattern ACTIVITY_TRANSITION =
            Pattern.compile("^/api/seckill/activities/[1-9][0-9]*/(?:publish|pause)$");
    private static final Pattern ACTIVITY_MANAGEMENT_READ =
            Pattern.compile("^/api/seckill/activities/manage/[1-9][0-9]*$");
    private static final String ACTIVITY_MANAGEMENT_LIST = "/api/seckill/activities/manage";
    private static final Pattern BUYER_ORDER_DETAIL =
            Pattern.compile("^/api/order/me/GS[0-9A-F]{32}$");
    private static final Pattern BUYER_ORDER_CANCEL =
            Pattern.compile("^/api/order/me/GS[0-9A-F]{32}/cancel$");
    private static final Pattern BUYER_ORDER_PAYMENT =
            Pattern.compile("^/api/order/me/GS[0-9A-F]{32}/payment$");
    private static final Pattern ADMIN_ORDER_REFUND =
            Pattern.compile("^/api/order/manage/GS[0-9A-F]{32}/refund$");
    private static final String ADMIN_AUDIT = "/api/order/manage/audit";
    private static final String ADMIN_MANAGED_ORDERS = "/api/order/manage/orders";
    private static final String ADMIN_INVENTORY_LEDGER = "/api/order/manage/inventory";
    private static final String ADMIN_OPERATIONS_METRICS = "/api/order/manage/metrics";
    private static final Pattern ADMIN_MANAGED_ORDER_DETAIL =
            Pattern.compile("^/api/order/manage/orders/GS[0-9A-F]{32}$");
    private static final Pattern ADMIN_ORDER_SHIPMENT =
            Pattern.compile("^/api/order/manage/orders/GS[0-9A-F]{32}/shipment$");
    private static final String TRADE_WEBSOCKET = "/api/web/ws/trades";
    private static final Pattern PUBLIC_PRODUCT_IMAGE =
            Pattern.compile("^/api/seckill/files/public/[0-9a-f]{32}$");
    private static final Pattern MANAGED_PRODUCT_IMAGE =
            Pattern.compile("^/api/seckill/files/[0-9a-f]{32}$");

    public boolean isDisabled(String path) {
        if (isBuyerOrderPath(path) || isAdminOrderPath(path) || isEnabledProductFilePath(path)
                || TRADE_WEBSOCKET.equals(path)) {
            return false;
        }
        return DISABLED_PREFIXES.stream().anyMatch(prefix -> path.equals(prefix)
                || path.startsWith(prefix + "/"))
                || AUTHORIZATION_LOOKUP_PATHS.contains(path);
    }

    public boolean isAnonymousAllowed(HttpMethod method, String path) {
        if (method == HttpMethod.GET
                && (path.equals("/api/seckill/activities") || ACTIVITY_DETAIL.matcher(path).matches())) {
            return true;
        }
        if (method == HttpMethod.POST
                && (path.equals("/api/auth/login") || path.equals("/api/auth/register"))) {
            return true;
        }
        if (method == HttpMethod.POST && path.equals("/api/payment/alipay/notify")) {
            return true;
        }
        if (method == HttpMethod.GET && PUBLIC_PRODUCT_IMAGE.matcher(path).matches()) {
            return true;
        }
        return false;
    }

    public Optional<List<String>> requiredAnyRole(HttpMethod method, String path) {
        if ((method == HttpMethod.GET && (path.equals("/api/order/me")
                || BUYER_ORDER_DETAIL.matcher(path).matches()))
                || (method == HttpMethod.POST && (BUYER_ORDER_CANCEL.matcher(path).matches()
                || BUYER_ORDER_PAYMENT.matcher(path).matches()))) {
            return Optional.of(List.of("buyer"));
        }
        if (method == HttpMethod.GET && ADMIN_AUDIT.equals(path)) {
            return Optional.of(List.of("system-manager"));
        }
        if ((method == HttpMethod.GET && (ADMIN_MANAGED_ORDERS.equals(path)
                || ADMIN_MANAGED_ORDER_DETAIL.matcher(path).matches() || ADMIN_INVENTORY_LEDGER.equals(path)
                || ADMIN_OPERATIONS_METRICS.equals(path)))
                || (method == HttpMethod.POST && ADMIN_ORDER_SHIPMENT.matcher(path).matches())) {
            return Optional.of(List.of("system-manager"));
        }
        if (method == HttpMethod.GET && TRADE_WEBSOCKET.equals(path)) {
            return Optional.of(List.of("buyer"));
        }
        if (method == HttpMethod.POST && ADMIN_ORDER_REFUND.matcher(path).matches()) {
            return Optional.of(List.of("system-manager"));
        }
        if (method == HttpMethod.POST && path.equals("/api/seckill/files/product-images")) {
            return Optional.of(List.of("system-manager", "goods-manager"));
        }
        if (method == HttpMethod.DELETE && MANAGED_PRODUCT_IMAGE.matcher(path).matches()) {
            return Optional.of(List.of("system-manager", "goods-manager"));
        }
        if (path.equals("/api/order/me") || path.startsWith("/api/order/me/")) {
            return Optional.empty();
        }
        if (method == HttpMethod.POST && TRADE_ORDER_CREATE.matcher(path).matches()) {
            return Optional.of(List.of("buyer"));
        }
        if ((method == HttpMethod.POST && (path.equals("/api/seckill/activities")
                || ACTIVITY_TRANSITION.matcher(path).matches()))
                || (method == HttpMethod.PUT && ACTIVITY_UPDATE.matcher(path).matches())) {
            return Optional.of(List.of("system-manager", "seckill-manager"));
        }
        if (method == HttpMethod.GET && (ACTIVITY_MANAGEMENT_LIST.equals(path)
                || ACTIVITY_MANAGEMENT_READ.matcher(path).matches())) {
            return Optional.of(List.of("system-manager", "seckill-manager"));
        }
        if (path.startsWith("/api/auth/user/") && !path.equals("/api/auth/user/info")
                && !AUTHORIZATION_LOOKUP_PATHS.contains(path)) {
            return Optional.of(List.of("system-manager"));
        }
        if (path.equals("/api/seckill/goods") || path.startsWith("/api/seckill/goods/")) {
            if (isAnonymousAllowed(method, path)) {
                return Optional.empty();
            }
            return Optional.of(List.of("system-manager", "goods-manager"));
        }
        if (path.equals("/api/seckill/seckill") || path.startsWith("/api/seckill/seckill/")) {
            if (isAnonymousAllowed(method, path)) {
                return Optional.empty();
            }
            return Optional.of(List.of("system-manager", "seckill-manager"));
        }
        if (path.equals("/api/order") || path.startsWith("/api/order/")) {
            return Optional.of(List.of("system-manager"));
        }
        if (path.equals("/api/common/oss/files") || path.startsWith("/api/common/oss/files/")) {
            return Optional.of(List.of("system-manager", "goods-manager"));
        }
        if (path.equals("/api/seata") || path.startsWith("/api/seata/")) {
            return Optional.of(List.of("system-manager"));
        }
        if (path.equals("/api/ai/assistant/chat") || path.equals("/api/auth/user/info") || path.equals("/api/auth/isLogin")
                || path.equals("/api/auth/tokenInfo") || path.equals("/api/auth/logout")) {
            return Optional.of(List.of());
        }
        // Unknown production routes are denied until they are explicitly classified.
        return Optional.empty();
    }

    private static boolean isBuyerOrderPath(String path) {
        return path.equals("/api/order/me") || BUYER_ORDER_DETAIL.matcher(path).matches()
                || BUYER_ORDER_CANCEL.matcher(path).matches() || BUYER_ORDER_PAYMENT.matcher(path).matches();
    }

    private static boolean isAdminOrderPath(String path) {
        return ADMIN_AUDIT.equals(path) || ADMIN_ORDER_REFUND.matcher(path).matches()
                || ADMIN_MANAGED_ORDERS.equals(path) || ADMIN_MANAGED_ORDER_DETAIL.matcher(path).matches()
                || ADMIN_ORDER_SHIPMENT.matcher(path).matches() || ADMIN_INVENTORY_LEDGER.equals(path)
                || ADMIN_OPERATIONS_METRICS.equals(path);
    }

    private static boolean isEnabledProductFilePath(String path) {
        return path.equals("/api/seckill/files/product-images")
                || PUBLIC_PRODUCT_IMAGE.matcher(path).matches()
                || MANAGED_PRODUCT_IMAGE.matcher(path).matches();
    }
}

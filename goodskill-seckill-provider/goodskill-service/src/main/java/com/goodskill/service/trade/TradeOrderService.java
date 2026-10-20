package com.goodskill.service.trade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Owns the local transaction that reserves stock and creates a pending-payment order.
 */
@Service
@Profile("production")
public class TradeOrderService {

    private static final String OPERATION_SCOPE = "ORDER_CREATE";
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{16,128}");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final int paymentDeadlineSeconds;
    private final boolean admissionEnabled;

    public TradeOrderService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${goodskill.trade.payment-deadline-seconds:900}") int paymentDeadlineSeconds,
            @Value("${goodskill.trade.admission-enabled:false}") boolean admissionEnabled) {
        if (paymentDeadlineSeconds < 60 || paymentDeadlineSeconds > 86_400) {
            throw new IllegalArgumentException("Payment deadline must be between 60 and 86400 seconds");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.paymentDeadlineSeconds = paymentDeadlineSeconds;
        this.admissionEnabled = admissionEnabled;
    }

    @Transactional
    public TradeOrderOutcome create(long activityId, String buyerId, String idempotencyKey,
                                    CreateTradeOrderRequest request) {
        validateCommand(activityId, buyerId, idempotencyKey, request);
        if (!admissionEnabled) {
            return TradeOrderOutcome.rejected("ADMISSION_PAUSED", 503);
        }

        String shippingSnapshot = toJson(request.shippingAddress());
        String requestHash = sha256(activityId + "\n" + request.skuId() + "\n"
                + request.quantity() + "\n" + shippingSnapshot);

        int inserted = jdbcTemplate.update("INSERT IGNORE INTO commerce_idempotency_request "
                        + "(buyer_id, operation_scope, idempotency_key, request_hash, request_state) "
                        + "VALUES (?, ?, ?, ?, 'IN_PROGRESS')",
                buyerId, OPERATION_SCOPE, idempotencyKey, requestHash);
        if (inserted == 0) {
            return replayExisting(buyerId, idempotencyKey, requestHash);
        }

        SaleSnapshot sale = findSale(activityId, request.skuId());
        if (sale == null) {
            return reject(buyerId, idempotencyKey, "ACTIVITY_NOT_FOUND", 404);
        }
        if (!sale.open() || sale.publishedVersion() <= 0) {
            return reject(buyerId, idempotencyKey, "ACTIVITY_NOT_OPEN", 409);
        }
        if (sale.availableQuantity() < request.quantity()) {
            return reject(buyerId, idempotencyKey, "SOLD_OUT", 409);
        }

        long totalAmount;
        try {
            totalAmount = Math.multiplyExact(sale.unitPriceMinor(), request.quantity().longValue());
        } catch (ArithmeticException overflow) {
            return reject(buyerId, idempotencyKey, "AMOUNT_OUT_OF_RANGE", 400);
        }

        jdbcTemplate.update("INSERT IGNORE INTO commerce_purchase_quota "
                        + "(buyer_id, activity_id, sku_id, reserved_quantity, purchased_quantity) "
                        + "VALUES (?, ?, ?, 0, 0)", buyerId, activityId, request.skuId());
        PurchaseQuota quota = jdbcTemplate.queryForObject(
                "SELECT reserved_quantity, purchased_quantity, row_version FROM commerce_purchase_quota "
                        + "WHERE buyer_id = ? AND activity_id = ? AND sku_id = ? FOR UPDATE",
                (rs, rowNum) -> new PurchaseQuota(rs.getLong(1), rs.getLong(2), rs.getLong(3)),
                buyerId, activityId, request.skuId());
        long existingQuantity;
        try {
            existingQuantity = Math.addExact(quota.reservedQuantity(), quota.purchasedQuantity());
        } catch (ArithmeticException overflow) {
            throw new IllegalStateException("Persisted buyer quota exceeds the supported range", overflow);
        }
        if (existingQuantity > sale.purchaseLimitPerUser() - request.quantity()) {
            return reject(buyerId, idempotencyKey, "PURCHASE_LIMIT_REACHED", 409);
        }

        int quotaUpdated = jdbcTemplate.update("UPDATE commerce_purchase_quota "
                        + "SET reserved_quantity = reserved_quantity + ?, row_version = row_version + 1 "
                        + "WHERE buyer_id = ? AND activity_id = ? AND sku_id = ? AND row_version = ? "
                        + "AND reserved_quantity + purchased_quantity + ? <= ?",
                request.quantity(), buyerId, activityId, request.skuId(), quota.rowVersion(),
                request.quantity(), sale.purchaseLimitPerUser());
        if (quotaUpdated != 1) {
            throw new IllegalStateException("Buyer quota changed after its row lock was acquired");
        }

        int inventoryUpdated = jdbcTemplate.update("UPDATE commerce_inventory "
                        + "SET available_quantity = available_quantity - ?, "
                        + "reserved_quantity = reserved_quantity + ?, row_version = row_version + 1 "
                        + "WHERE activity_id = ? AND sku_id = ? AND row_version = ? AND available_quantity >= ?",
                request.quantity(), request.quantity(), activityId, request.skuId(),
                sale.inventoryVersion(), request.quantity());
        if (inventoryUpdated != 1) {
            throw new IllegalStateException("Inventory changed after its row lock was acquired");
        }

        String orderNo = "GS" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String reservationNo = "RS" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Instant paymentDeadlineInstant = sale.databaseNow().plusSeconds(paymentDeadlineSeconds);
        LocalDateTime paymentDeadline = LocalDateTime.ofInstant(paymentDeadlineInstant, ZoneOffset.UTC);
        jdbcTemplate.update("INSERT INTO commerce_order "
                        + "(order_no, buyer_id, activity_id, sku_id, activity_version, product_name_snapshot, "
                        + "sku_name_snapshot, quantity, unit_price_minor, total_amount_minor, currency, "
                        + "shipping_snapshot, order_state, fulfillment_state, idempotency_scope, "
                        + "idempotency_key, payment_deadline, row_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CNY', ?, 'PENDING_PAYMENT', 'UNFULFILLED', ?, ?, ?, 0)",
                orderNo, buyerId, activityId, request.skuId(), sale.publishedVersion(), sale.productName(),
                sale.skuName(), request.quantity(), sale.unitPriceMinor(), totalAmount, shippingSnapshot,
                OPERATION_SCOPE, idempotencyKey, paymentDeadline);

        jdbcTemplate.update("INSERT INTO commerce_reservation "
                        + "(reservation_no, order_no, buyer_id, activity_id, sku_id, quantity, "
                        + "reservation_state, expires_at, row_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'HELD', ?, 0)",
                reservationNo, orderNo, buyerId, activityId, request.skuId(), request.quantity(), paymentDeadline);

        jdbcTemplate.update("INSERT INTO commerce_inventory_ledger "
                        + "(operation_key, activity_id, sku_id, order_no, actor_id, operation_type, "
                        + "available_delta, reserved_delta, sold_delta, reason) "
                        + "VALUES (?, ?, ?, ?, ?, 'ORDER_RESERVATION', ?, ?, 0, ?)",
                "RESERVE:" + orderNo, activityId, request.skuId(), orderNo, buyerId,
                -request.quantity(), request.quantity(), "Pending-payment order reserved inventory");

        jdbcTemplate.update("INSERT INTO commerce_order_status_history "
                        + "(order_no, transition_key, from_state, to_state, actor_id, reason, row_version) "
                        + "VALUES (?, ?, NULL, 'PENDING_PAYMENT', ?, 'Order created', 0)",
                orderNo, "CREATE:" + orderNo, buyerId);

        String payload = toJson(Map.of(
                "orderNo", orderNo,
                "buyerId", buyerId,
                "activityId", activityId,
                "skuId", request.skuId(),
                "quantity", request.quantity(),
                "totalAmountMinor", totalAmount,
                "currency", "CNY"));
        jdbcTemplate.update("INSERT INTO commerce_outbox "
                        + "(event_id, aggregate_type, aggregate_id, event_type, event_version, payload) "
                        + "VALUES (?, 'ORDER', ?, 'OrderCreated', 1, ?)",
                UUID.randomUUID().toString(), orderNo, payload);

        jdbcTemplate.update("UPDATE commerce_idempotency_request SET request_state = 'COMPLETED', "
                        + "result_order_no = ? WHERE buyer_id = ? AND operation_scope = ? AND idempotency_key = ?",
                orderNo, buyerId, OPERATION_SCOPE, idempotencyKey);
        TradeAuditWriter.record(jdbcTemplate, objectMapper, buyerId, "ORDER_CREATE", "ORDER", orderNo,
                "SUCCESS", null, Map.of("activityId", activityId, "skuId", request.skuId(),
                        "quantity", request.quantity(), "amountMinor", totalAmount, "currency", "CNY"));

        return TradeOrderOutcome.success(new TradeOrderResponse(orderNo, "PENDING_PAYMENT",
                paymentDeadlineInstant, totalAmount, "CNY", false));
    }

    private TradeOrderOutcome replayExisting(String buyerId, String idempotencyKey, String requestHash) {
        IdempotencyRecord record = jdbcTemplate.queryForObject(
                "SELECT request_hash, request_state, result_order_no, result_code "
                        + "FROM commerce_idempotency_request "
                        + "WHERE buyer_id = ? AND operation_scope = ? AND idempotency_key = ? FOR UPDATE",
                (rs, rowNum) -> new IdempotencyRecord(rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4)),
                buyerId, OPERATION_SCOPE, idempotencyKey);
        if (record == null) {
            throw new IllegalStateException("Idempotency row disappeared after a duplicate insert");
        }
        if (!MessageDigest.isEqual(record.requestHash().getBytes(StandardCharsets.US_ASCII),
                requestHash.getBytes(StandardCharsets.US_ASCII))) {
            return TradeOrderOutcome.rejected("IDEMPOTENCY_CONFLICT", 409);
        }
        if ("REJECTED".equals(record.state())) {
            String errorCode = record.resultCode() == null ? "ORDER_REJECTED" : record.resultCode();
            return TradeOrderOutcome.rejected(errorCode, statusFor(errorCode));
        }
        if (!"COMPLETED".equals(record.state()) || record.orderNo() == null) {
            return TradeOrderOutcome.rejected("IDEMPOTENCY_IN_PROGRESS", 409);
        }

        TradeOrderResponse order = jdbcTemplate.queryForObject(
                "SELECT order_no, order_state, payment_deadline, total_amount_minor, currency "
                        + "FROM commerce_order WHERE order_no = ? AND buyer_id = ?",
                (rs, rowNum) -> new TradeOrderResponse(rs.getString(1), rs.getString(2),
                        rs.getObject(3, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getLong(4), rs.getString(5), true),
                record.orderNo(), buyerId);
        if (order == null) {
            throw new IllegalStateException("Completed idempotency key references a missing order");
        }
        return TradeOrderOutcome.success(order);
    }

    private SaleSnapshot findSale(long activityId, long skuId) {
        var matches = jdbcTemplate.query(
                "SELECT (a.state = 'PUBLISHED' AND a.starts_at <= UTC_TIMESTAMP(6) "
                        + "AND a.ends_at > UTC_TIMESTAMP(6)) AS sale_open, a.published_version, "
                        + "s.product_name_snapshot, s.sku_name_snapshot, s.unit_price_minor, "
                        + "s.purchase_limit_per_user, i.available_quantity, i.row_version, UTC_TIMESTAMP(6) "
                        + "FROM commerce_activity a "
                        + "JOIN commerce_activity_sku s ON s.activity_id = a.activity_id "
                        + "JOIN commerce_inventory i ON i.activity_id = s.activity_id AND i.sku_id = s.sku_id "
                        + "WHERE a.activity_id = ? AND s.sku_id = ? FOR UPDATE",
                (rs, rowNum) -> new SaleSnapshot(rs.getBoolean(1), rs.getLong(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5), rs.getInt(6), rs.getLong(7), rs.getLong(8),
                        rs.getObject(9, LocalDateTime.class).toInstant(ZoneOffset.UTC)),
                activityId, skuId);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private TradeOrderOutcome reject(String buyerId, String idempotencyKey, String errorCode, int status) {
        jdbcTemplate.update("UPDATE commerce_idempotency_request SET request_state = 'REJECTED', result_code = ? "
                        + "WHERE buyer_id = ? AND operation_scope = ? AND idempotency_key = ?",
                errorCode, buyerId, OPERATION_SCOPE, idempotencyKey);
        TradeAuditWriter.record(jdbcTemplate, objectMapper, buyerId, "ORDER_CREATE_REJECTED", "ORDER_REQUEST",
                sha256(idempotencyKey), "REJECTED", errorCode, Map.of("status", status));
        return TradeOrderOutcome.rejected(errorCode, status);
    }

    private void validateCommand(long activityId, String buyerId, String idempotencyKey,
                                 CreateTradeOrderRequest request) {
        if (activityId <= 0 || !StringUtils.hasText(buyerId) || buyerId.length() > 64
                || !StringUtils.hasText(idempotencyKey) || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()
                || request == null || request.skuId() == null || request.skuId() <= 0
                || request.quantity() == null || request.quantity() < 1 || request.quantity() > 100
                || request.shippingAddress() == null) {
            throw new IllegalArgumentException("Invalid order command");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize a commerce snapshot", error);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private static int statusFor(String errorCode) {
        return "ACTIVITY_NOT_FOUND".equals(errorCode) ? 404
                : "AMOUNT_OUT_OF_RANGE".equals(errorCode) ? 400 : 409;
    }

    private record SaleSnapshot(boolean open, long publishedVersion, String productName, String skuName,
                                long unitPriceMinor, int purchaseLimitPerUser, long availableQuantity,
                                long inventoryVersion, Instant databaseNow) {
    }

    private record PurchaseQuota(long reservedQuantity, long purchasedQuantity, long rowVersion) {
    }

    private record IdempotencyRecord(String requestHash, String state, String orderNo, String resultCode) {
    }
}

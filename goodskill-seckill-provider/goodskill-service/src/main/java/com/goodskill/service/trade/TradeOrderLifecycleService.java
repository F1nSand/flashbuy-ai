package com.goodskill.service.trade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Profile("production")
public class TradeOrderLifecycleService {

    private static final String CANCEL_SCOPE = "ORDER_CANCEL";
    private static final Pattern ORDER_NO = Pattern.compile("GS[0-9A-F]{32}");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{16,128}");
    private static final long MAX_PAGE = 1_000_000;
    private static final int MAX_EXPIRY_BATCH = 100;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TradeOrderLifecycleService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public TradeOrderPageResponse list(String buyerId, long page, int size) {
        validateBuyer(buyerId);
        if (page < 1 || page > MAX_PAGE || size < 1 || size > 50) {
            throw new TradeOrderApiException(400, "VALIDATION_FAILED");
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM commerce_order WHERE buyer_id = ?", Long.class, buyerId);
        long offset = Math.multiplyExact(page - 1, size);
        List<TradeOrderResponse> orders = jdbcTemplate.query(
                "SELECT order_no, order_state, payment_deadline, total_amount_minor, currency "
                        + "FROM commerce_order WHERE buyer_id = ? "
                        + "ORDER BY created_at DESC, order_id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new TradeOrderResponse(rs.getString(1), rs.getString(2),
                        rs.getObject(3, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getLong(4), rs.getString(5), false), buyerId, size, offset);
        return new TradeOrderPageResponse(page, size, total == null ? 0 : total, orders);
    }

    @Transactional(readOnly = true)
    public TradeOrderDetailResponse detail(String buyerId, String orderNo) {
        validateBuyer(buyerId);
        validateOrderNo(orderNo);
        List<TradeOrderDetailResponse> orders = jdbcTemplate.query(
                "SELECT order_no, order_state, payment_deadline, total_amount_minor, currency, "
                        + "shipping_snapshot, fulfillment_state, product_name_snapshot, sku_name_snapshot, quantity "
                        + "FROM commerce_order "
                        + "WHERE order_no = ? AND buyer_id = ?",
                (rs, rowNum) -> new TradeOrderDetailResponse(
                        new TradeOrderResponse(rs.getString(1), rs.getString(2),
                                rs.getObject(3, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                                rs.getLong(4), rs.getString(5), false),
                        readShippingAddress(rs.getString(6)), rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getInt(10)),
                orderNo, buyerId);
        if (orders.isEmpty()) {
            throw new TradeOrderApiException(404, "ORDER_NOT_FOUND");
        }
        return orders.getFirst();
    }

    @Transactional
    public TradeOrderOutcome cancel(String buyerId, String orderNo, String idempotencyKey) {
        validateBuyer(buyerId);
        validateOrderNo(orderNo);
        if (!StringUtils.hasText(idempotencyKey) || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new TradeOrderApiException(400, "VALIDATION_FAILED");
        }
        String requestHash = sha256("ORDER_CANCEL\n" + orderNo);
        int inserted = jdbcTemplate.update("INSERT IGNORE INTO commerce_idempotency_request "
                        + "(buyer_id, operation_scope, idempotency_key, request_hash, request_state) "
                        + "VALUES (?, ?, ?, ?, 'IN_PROGRESS')",
                buyerId, CANCEL_SCOPE, idempotencyKey, requestHash);
        if (inserted == 0) {
            return replayCancellation(buyerId, orderNo, idempotencyKey, requestHash);
        }

        OrderLock order = lockOrder(buyerId, orderNo);
        if (order == null) {
            return reject(buyerId, idempotencyKey, "ORDER_NOT_FOUND", 404);
        }
        if (!"PENDING_PAYMENT".equals(order.state())) {
            return reject(buyerId, idempotencyKey, "ORDER_STATE_CONFLICT", 409);
        }
        Integer activePayment = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM commerce_payment_attempt "
                        + "WHERE order_no = ? AND payment_state IN ('CREATED', 'PENDING', 'CONFIRMING')",
                Integer.class, orderNo);
        if (activePayment != null && activePayment > 0) {
            return reject(buyerId, idempotencyKey, "PAYMENT_IN_PROGRESS", 409);
        }

        releasePendingOrder(order, orderNo, buyerId, "RELEASE:" + orderNo, "CANCEL:" + orderNo,
                "Buyer cancelled pending-payment order", "BUYER_CANCELLED");
        jdbcTemplate.update("UPDATE commerce_idempotency_request SET request_state = 'COMPLETED', "
                        + "result_order_no = ? WHERE buyer_id = ? AND operation_scope = ? AND idempotency_key = ?",
                orderNo, buyerId, CANCEL_SCOPE, idempotencyKey);

        return TradeOrderOutcome.success(new TradeOrderResponse(orderNo, "CLOSED", order.paymentDeadline(),
                order.totalAmountMinor(), order.currency(), false));
    }

    @Transactional
    public PaymentCheckoutDetails preparePayment(String buyerId, String orderNo, String mode) {
        validateBuyer(buyerId);
        validateOrderNo(orderNo);
        if (!(mode == null || "page".equals(mode) || "wap".equals(mode))) {
            throw new TradeOrderApiException(400, "VALIDATION_FAILED");
        }
        OrderLock order = lockOrder(buyerId, orderNo);
        if (order == null) {
            throw new TradeOrderApiException(404, "ORDER_NOT_FOUND");
        }
        if (!"PENDING_PAYMENT".equals(order.state())) {
            throw new TradeOrderApiException(409, "ORDER_STATE_CONFLICT");
        }
        if (order.totalAmountMinor() <= 0) {
            throw new TradeOrderApiException(409, "PAYMENT_AMOUNT_INVALID");
        }
        Long remainingSeconds = jdbcTemplate.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(6), payment_deadline) "
                        + "FROM commerce_order WHERE order_no = ?",
                Long.class, orderNo);
        if (remainingSeconds == null || remainingSeconds < 60) {
            throw new TradeOrderApiException(409, "ORDER_EXPIRED");
        }
        List<PaymentAttemptLock> existing = jdbcTemplate.query(
                "SELECT payment_no, amount_minor, payment_state FROM commerce_payment_attempt "
                        + "WHERE order_no = ? AND provider = 'ALIPAY' FOR UPDATE",
                (rs, rowNum) -> new PaymentAttemptLock(rs.getString(1), rs.getLong(2), rs.getString(3)), orderNo);
        if (!existing.isEmpty()) {
            PaymentAttemptLock attempt = existing.getFirst();
            if (!("CREATED".equals(attempt.state()) || "PENDING".equals(attempt.state())
                    || "CONFIRMING".equals(attempt.state()))) {
                throw new TradeOrderApiException(409, "PAYMENT_ATTEMPT_TERMINAL");
            }
            String subject = findOrderSubject(orderNo);
            return new PaymentCheckoutDetails(attempt.paymentNo(), subject, attempt.amountMinor(),
                    remainingSeconds, mode);
        }

        String paymentNo = "GP" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        jdbcTemplate.update("INSERT INTO commerce_payment_attempt "
                        + "(payment_no, order_no, provider, amount_minor, currency, payment_state, row_version) "
                        + "VALUES (?, ?, 'ALIPAY', ?, 'CNY', 'CREATED', 0)",
                paymentNo, orderNo, order.totalAmountMinor());
        TradeAuditWriter.record(jdbcTemplate, objectMapper, buyerId, "PAYMENT_CHECKOUT_CREATE", "PAYMENT",
                paymentNo, "SUCCESS", null, Map.of("orderNo", orderNo, "amountMinor", order.totalAmountMinor(),
                        "currency", order.currency(), "mode", mode == null ? "configured" : mode));
        return new PaymentCheckoutDetails(paymentNo, findOrderSubject(orderNo), order.totalAmountMinor(),
                remainingSeconds, mode);
    }

    @Transactional
    public void markPaymentPending(String paymentNo) {
        int updated = jdbcTemplate.update("UPDATE commerce_payment_attempt SET payment_state = 'PENDING', "
                        + "row_version = row_version + 1 WHERE payment_no = ? AND payment_state = 'CREATED'",
                paymentNo);
        if (updated == 0) {
            Integer stillPending = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM commerce_payment_attempt "
                    + "WHERE payment_no = ? AND payment_state = 'PENDING'", Integer.class, paymentNo);
            if (stillPending == null || stillPending != 1) {
                throw new TradeOrderApiException(409, "PAYMENT_ATTEMPT_TERMINAL");
            }
        }
    }

    @Transactional
    public void recordNonPaidNotification(TradePaymentProvider.VerifiedNotification notification) {
        if (notification == null || notification.paymentNo() == null || notification.amountMinor() <= 0) {
            throw new TradeOrderApiException(400, "PAYMENT_NOTIFICATION_INVALID");
        }
        String orderNo = findPaymentOrder(notification.paymentNo());
        if (orderNo == null) {
            throw new TradeOrderApiException(404, "PAYMENT_ATTEMPT_NOT_FOUND");
        }
        OrderLock order = lockOrderForPayment(orderNo);
        PaymentAttemptLock attempt = lockPaymentAttempt(notification.paymentNo());
        if (order == null || attempt == null || attempt.amountMinor() != notification.amountMinor()
                || order.totalAmountMinor() != notification.amountMinor()) {
            throw new TradeOrderApiException(400, "PAYMENT_AMOUNT_MISMATCH");
        }
        if ("TRADE_CLOSED".equals(notification.tradeStatus())) {
            jdbcTemplate.update("UPDATE commerce_payment_attempt SET payment_state = 'CLOSED', "
                            + "provider_trade_no = COALESCE(provider_trade_no, ?), row_version = row_version + 1 "
                            + "WHERE payment_no = ? AND provider = 'ALIPAY' "
                            + "AND payment_state IN ('CREATED', 'PENDING', 'CONFIRMING')",
                    notification.providerTradeNo(), notification.paymentNo());
        } else if (!"WAIT_BUYER_PAY".equals(notification.tradeStatus())) {
            throw new TradeOrderApiException(400, "PAYMENT_NOTIFICATION_INVALID");
        }
        if ("TRADE_CLOSED".equals(notification.tradeStatus())) {
            TradeAuditWriter.record(jdbcTemplate, objectMapper, "system:alipay", "PAYMENT_CLOSED", "PAYMENT",
                    notification.paymentNo(), "SUCCESS", null, Map.of("orderNo", orderNo));
        }
    }

    @Transactional
    public void applyConfirmedPayment(TradePaymentProvider.ConfirmedPayment payment) {
        if (payment == null || payment.paymentNo() == null || payment.providerTradeNo() == null
                || payment.providerTradeNo().isBlank() || payment.providerTradeNo().length() > 128
                || payment.amountMinor() <= 0) {
            throw new TradeOrderApiException(400, "PAYMENT_NOTIFICATION_INVALID");
        }
        String orderNo = findPaymentOrder(payment.paymentNo());
        if (orderNo == null) {
            throw new TradeOrderApiException(404, "PAYMENT_ATTEMPT_NOT_FOUND");
        }
        OrderLock order = lockOrderForPayment(orderNo);
        PaymentAttemptLock attempt = lockPaymentAttempt(payment.paymentNo());
        if (order == null || attempt == null || !"ALIPAY".equals(attempt.provider())) {
            throw new TradeOrderApiException(404, "PAYMENT_ATTEMPT_NOT_FOUND");
        }
        if (attempt.amountMinor() != payment.amountMinor() || order.totalAmountMinor() != payment.amountMinor()) {
            throw new TradeOrderApiException(400, "PAYMENT_AMOUNT_MISMATCH");
        }
        if ("SUCCEEDED".equals(attempt.state())) {
            if (!payment.providerTradeNo().equals(attempt.providerTradeNo())) {
                throw new TradeOrderApiException(409, "PAYMENT_TRADE_MISMATCH");
            }
            return;
        }
        if (attempt.providerTradeNo() != null && !payment.providerTradeNo().equals(attempt.providerTradeNo())) {
            throw new TradeOrderApiException(409, "PAYMENT_TRADE_MISMATCH");
        }

        if ("CLOSED".equals(order.state())) {
            int marked = jdbcTemplate.update("UPDATE commerce_payment_attempt SET payment_state = 'SUCCEEDED', "
                            + "provider_trade_no = ?, paid_at = UTC_TIMESTAMP(6), row_version = row_version + 1 "
                            + "WHERE payment_no = ? AND payment_state <> 'SUCCEEDED'",
                    payment.providerTradeNo(), payment.paymentNo());
            if (marked != 1) {
                throw new IllegalStateException("Late payment attempt changed while its rows were locked");
            }
            enqueueLatePaymentRefund(orderNo, order, payment);
            TradeAuditWriter.record(jdbcTemplate, objectMapper, "system:alipay", "PAYMENT_LATE_SUCCESS", "ORDER",
                    orderNo, "COMPENSATION_REQUIRED", "Order was already closed",
                    Map.of("paymentNo", payment.paymentNo(), "amountMinor", payment.amountMinor(),
                            "currency", order.currency()));
            return;
        }
        if (!("PENDING_PAYMENT".equals(order.state()) || "PAYMENT_CONFIRMING".equals(order.state()))) {
            throw new TradeOrderApiException(409, "ORDER_STATE_CONFLICT");
        }
        consumeReservedOrder(orderNo, order, payment);
        TradeAuditWriter.record(jdbcTemplate, objectMapper, "system:alipay", "PAYMENT_SETTLED", "ORDER", orderNo,
                "SUCCESS", null, Map.of("paymentNo", payment.paymentNo(),
                        "providerTradeNo", payment.providerTradeNo(), "amountMinor", payment.amountMinor(),
                        "currency", order.currency()));
    }

    @Transactional
    public PaymentCheck prepareExpiryCheck(String orderNo) {
        validateOrderNo(orderNo);
        List<OrderLock> orders = jdbcTemplate.query("SELECT buyer_id, order_state, row_version, activity_id, sku_id, "
                        + "quantity, payment_deadline, total_amount_minor, currency FROM commerce_order "
                        + "WHERE order_no = ? AND order_state IN ('PENDING_PAYMENT', 'PAYMENT_CONFIRMING') "
                        + "AND payment_deadline <= UTC_TIMESTAMP(6) FOR UPDATE",
                (rs, rowNum) -> readOrderLock(rs), orderNo);
        if (orders.isEmpty()) {
            return null;
        }
        OrderLock order = orders.getFirst();
        List<PaymentAttemptLock> attempts = jdbcTemplate.query("SELECT payment_no, amount_minor, payment_state "
                        + "FROM commerce_payment_attempt WHERE order_no = ? AND provider = 'ALIPAY' "
                        + "ORDER BY created_at DESC LIMIT 1 FOR UPDATE",
                (rs, rowNum) -> new PaymentAttemptLock(rs.getString(1), rs.getLong(2), rs.getString(3)), orderNo);
        if (attempts.isEmpty()) {
            return new PaymentCheck(orderNo, null, order.totalAmountMinor());
        }
        PaymentAttemptLock attempt = attempts.getFirst();
        if ("PENDING_PAYMENT".equals(order.state())) {
            int updated = jdbcTemplate.update("UPDATE commerce_order SET order_state = 'PAYMENT_CONFIRMING', "
                            + "row_version = row_version + 1 WHERE order_no = ? AND order_state = 'PENDING_PAYMENT' "
                            + "AND row_version = ?",
                    orderNo, order.rowVersion());
            if (updated != 1) {
                throw new IllegalStateException("Expired order changed while its row lock was held");
            }
            jdbcTemplate.update("INSERT INTO commerce_order_status_history "
                            + "(order_no, transition_key, from_state, to_state, actor_id, reason, row_version) "
                            + "VALUES (?, ?, 'PENDING_PAYMENT', 'PAYMENT_CONFIRMING', 'system:expiry-closer', ?, ?)",
                    orderNo, "PAYMENT_CHECK:" + orderNo, "Checking Alipay before releasing reservation",
                    order.rowVersion() + 1);
        }
        if ("CREATED".equals(attempt.state()) || "PENDING".equals(attempt.state())) {
            jdbcTemplate.update("UPDATE commerce_payment_attempt SET payment_state = 'CONFIRMING', "
                            + "row_version = row_version + 1 WHERE payment_no = ? AND payment_state IN ('CREATED', 'PENDING')",
                    attempt.paymentNo());
        }
        return new PaymentCheck(orderNo, attempt.paymentNo(), attempt.amountMinor());
    }

    @Transactional
    public boolean closeExpiredAfterProviderClose(String orderNo, String paymentNo) {
        validateOrderNo(orderNo);
        List<OrderLock> orders = jdbcTemplate.query("SELECT buyer_id, order_state, row_version, activity_id, sku_id, "
                        + "quantity, payment_deadline, total_amount_minor, currency FROM commerce_order "
                        + "WHERE order_no = ? AND order_state IN ('PENDING_PAYMENT', 'PAYMENT_CONFIRMING') "
                        + "AND payment_deadline <= UTC_TIMESTAMP(6) FOR UPDATE",
                (rs, rowNum) -> readOrderLock(rs), orderNo);
        if (orders.isEmpty()) {
            return false;
        }
        OrderLock order = orders.getFirst();
        if (paymentNo != null) {
            PaymentAttemptLock attempt = lockPaymentAttempt(paymentNo);
            if (attempt == null || !orderNo.equals(attempt.orderNo()) || !"ALIPAY".equals(attempt.provider())) {
                throw new IllegalStateException("Expiry task references a mismatched payment attempt");
            }
            if ("SUCCEEDED".equals(attempt.state())) {
                throw new IllegalStateException("A paid attempt cannot release an expired order reservation");
            }
            if (!"CLOSED".equals(attempt.state())) {
                jdbcTemplate.update("UPDATE commerce_payment_attempt SET payment_state = 'CLOSED', "
                                + "row_version = row_version + 1 WHERE payment_no = ? "
                                + "AND payment_state IN ('CREATED', 'PENDING', 'CONFIRMING', 'FAILED')",
                        paymentNo);
            }
        } else {
            Integer attemptCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM commerce_payment_attempt WHERE order_no = ?", Integer.class, orderNo);
            if (attemptCount != null && attemptCount > 0) {
                throw new IllegalStateException("Cannot release expired order without checking its payment attempt");
            }
        }
        releasePendingOrder(order, orderNo, "system:expiry-closer", "EXPIRE:" + orderNo, "EXPIRE:" + orderNo,
                "Payment deadline expired after channel check", "PAYMENT_DEADLINE_EXPIRED");
        return true;
    }

    @Transactional
    public RefundPlan requestFullRefund(String actorId, String orderNo, String idempotencyKey, String reason) {
        validateBuyer(actorId);
        validateOrderNo(orderNo);
        if (!StringUtils.hasText(idempotencyKey) || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()
                || !StringUtils.hasText(reason) || reason.length() > 500) {
            throw new TradeOrderApiException(400, "VALIDATION_FAILED");
        }
        List<RefundableOrder> orders = jdbcTemplate.query("SELECT order_state, fulfillment_state, row_version, "
                        + "total_amount_minor FROM commerce_order WHERE order_no = ? FOR UPDATE",
                (rs, rowNum) -> new RefundableOrder(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4)),
                orderNo);
        if (orders.isEmpty()) {
            throw new TradeOrderApiException(404, "ORDER_NOT_FOUND");
        }
        RefundableOrder order = orders.getFirst();
        List<ExistingRefund> existing = jdbcTemplate.query("SELECT r.refund_no, r.order_no, p.payment_no, "
                        + "r.amount_minor, r.reason, r.refund_state, o.order_state, r.requested_by, "
                        + "r.idempotency_key "
                        + "FROM commerce_refund r JOIN commerce_order o ON o.order_no = r.order_no "
                        + "LEFT JOIN commerce_payment_attempt p ON p.order_no = r.order_no "
                        + "AND p.payment_state = 'SUCCEEDED' WHERE r.order_no = ?",
                (rs, rowNum) -> new ExistingRefund(
                        new RefundPlan(rs.getString(1), rs.getString(2), rs.getString(3),
                                rs.getLong(4), rs.getString(5), rs.getString(6), rs.getString(7)),
                        rs.getString(8), rs.getString(9)), orderNo);
        if (!existing.isEmpty()) {
            ExistingRefund stored = existing.getFirst();
            if (!idempotencyKey.equals(stored.idempotencyKey())) {
                throw new TradeOrderApiException(409, "REFUND_ALREADY_REQUESTED");
            }
            if (!actorId.equals(stored.requestedBy()) || !reason.equals(stored.plan().reason())) {
                throw new TradeOrderApiException(409, "IDEMPOTENCY_CONFLICT");
            }
            return stored.plan();
        }
        if (!"PAID".equals(order.state()) || !"UNFULFILLED".equals(order.fulfillmentState())) {
            throw new TradeOrderApiException(409, "ORDER_NOT_REFUNDABLE");
        }
        String paymentNo = jdbcTemplate.queryForObject("SELECT payment_no FROM commerce_payment_attempt "
                + "WHERE order_no = ? AND payment_state = 'SUCCEEDED'", String.class, orderNo);
        String refundNo = "RF" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        jdbcTemplate.update("INSERT INTO commerce_refund "
                        + "(refund_no, order_no, idempotency_key, amount_minor, currency, refund_state, "
                        + "requested_by, reason, row_version) VALUES (?, ?, ?, ?, 'CNY', 'PENDING', ?, ?, 0)",
                refundNo, orderNo, idempotencyKey, order.amountMinor(), actorId, reason);
        int updated = jdbcTemplate.update("UPDATE commerce_order SET order_state = 'REFUNDING', "
                        + "row_version = row_version + 1 WHERE order_no = ? AND order_state = 'PAID' AND row_version = ?",
                orderNo, order.rowVersion());
        if (updated != 1) {
            throw new IllegalStateException("Order changed while its refund was being requested");
        }
        jdbcTemplate.update("INSERT INTO commerce_order_status_history "
                        + "(order_no, transition_key, from_state, to_state, actor_id, reason, row_version) "
                        + "VALUES (?, ?, 'PAID', 'REFUNDING', ?, ?, ?)",
                orderNo, "REFUND_REQUEST:" + refundNo, actorId, reason, order.rowVersion() + 1);
        jdbcTemplate.update("INSERT INTO commerce_compensation_task "
                        + "(task_key, task_type, aggregate_id, task_state, available_at) "
                + "VALUES (?, 'PROCESS_REFUND', ?, 'PENDING', UTC_TIMESTAMP(6))",
                "REFUND:" + refundNo, orderNo);
        TradeAuditWriter.record(jdbcTemplate, objectMapper, actorId, "REFUND_REQUEST", "REFUND", refundNo,
                "SUCCESS", reason, Map.of("orderNo", orderNo, "amountMinor", order.amountMinor(),
                        "currency", "CNY"));
        return new RefundPlan(refundNo, orderNo, paymentNo, order.amountMinor(), reason, "PENDING", "REFUNDING");
    }

    @Transactional(readOnly = true)
    public RefundPlan loadRefund(String orderNo) {
        validateOrderNo(orderNo);
        List<RefundPlan> rows = jdbcTemplate.query("SELECT r.refund_no, r.order_no, p.payment_no, "
                        + "r.amount_minor, r.reason, r.refund_state, o.order_state "
                        + "FROM commerce_refund r JOIN commerce_order o ON o.order_no = r.order_no "
                        + "LEFT JOIN commerce_payment_attempt p ON p.order_no = r.order_no "
                        + "AND p.payment_state = 'SUCCEEDED' WHERE r.order_no = ?",
                (rs, rowNum) -> new RefundPlan(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getString(5), rs.getString(6), rs.getString(7)), orderNo);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Transactional
    public void recordRefundOutcome(String refundNo, TradePaymentProvider.RefundStatus status) {
        List<RefundPlan> rows = jdbcTemplate.query("SELECT r.refund_no, r.order_no, p.payment_no, "
                        + "r.amount_minor, r.reason, r.refund_state, o.order_state "
                        + "FROM commerce_refund r JOIN commerce_order o ON o.order_no = r.order_no "
                        + "LEFT JOIN commerce_payment_attempt p ON p.order_no = r.order_no "
                        + "AND p.payment_state = 'SUCCEEDED' WHERE r.refund_no = ? FOR UPDATE",
                (rs, rowNum) -> new RefundPlan(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getString(5), rs.getString(6), rs.getString(7)), refundNo);
        if (rows.isEmpty()) {
            throw new TradeOrderApiException(404, "REFUND_NOT_FOUND");
        }
        RefundPlan refund = rows.getFirst();
        if ("SUCCEEDED".equals(refund.state()) || "FAILED".equals(refund.state())) {
            return;
        }
        if (status == TradePaymentProvider.RefundStatus.UNKNOWN) {
            throw new PaymentChannelException("Alipay refund state is unknown");
        }
        if (status == TradePaymentProvider.RefundStatus.PENDING) {
            jdbcTemplate.update("UPDATE commerce_refund SET refund_state = 'CONFIRMING', "
                            + "row_version = row_version + 1 WHERE refund_no = ? "
                            + "AND refund_state IN ('PENDING', 'CONFIRMING')",
                    refundNo);
            return;
        }
        String finalState = status == TradePaymentProvider.RefundStatus.SUCCEEDED ? "SUCCEEDED" : "FAILED";
        jdbcTemplate.update("UPDATE commerce_refund SET refund_state = ?, "
                        + "refunded_at = CASE WHEN ? = 'SUCCEEDED' THEN UTC_TIMESTAMP(6) ELSE refunded_at END, "
                        + "row_version = row_version + 1 WHERE refund_no = ? "
                        + "AND refund_state IN ('PENDING', 'CONFIRMING')",
                finalState, finalState, refundNo);
        if ("REFUNDING".equals(refund.orderState())) {
            String nextOrderState = "SUCCEEDED".equals(finalState) ? "REFUNDED" : "PAID";
            int updated = jdbcTemplate.update("UPDATE commerce_order SET order_state = ?, row_version = row_version + 1 "
                            + "WHERE order_no = ? AND order_state = 'REFUNDING'",
                    nextOrderState, refund.orderNo());
            if (updated != 1) {
                throw new IllegalStateException("Order state changed while refund outcome was recorded");
            }
            Long version = jdbcTemplate.queryForObject("SELECT row_version FROM commerce_order WHERE order_no = ?",
                    Long.class, refund.orderNo());
            jdbcTemplate.update("INSERT INTO commerce_order_status_history "
                            + "(order_no, transition_key, from_state, to_state, actor_id, reason, row_version) "
                            + "VALUES (?, ?, 'REFUNDING', ?, 'system:alipay', ?, ?)",
                    refund.orderNo(), "REFUND_RESULT:" + refund.refundNo(), nextOrderState,
                    "Alipay full refund " + finalState.toLowerCase(), version);
        }
        String resultingOrderState = "REFUNDING".equals(refund.orderState())
                ? ("SUCCEEDED".equals(finalState) ? "REFUNDED" : "PAID") : refund.orderState();
        jdbcTemplate.update("INSERT INTO commerce_outbox "
                        + "(event_id, aggregate_type, aggregate_id, event_type, event_version, payload) "
                        + "VALUES (?, 'ORDER', ?, ?, 1, ?)",
                UUID.randomUUID().toString(), refund.orderNo(),
                "SUCCEEDED".equals(finalState) ? "OrderRefunded" : "OrderRefundFailed",
                toJson(Map.of("orderNo", refund.orderNo(), "buyerId", findOrderBuyerId(refund.orderNo()),
                        "orderState", resultingOrderState,
                        "refundNo", refund.refundNo(),
                        "amountMinor", refund.amountMinor(), "currency", "CNY")));
        TradeAuditWriter.record(jdbcTemplate, objectMapper, "system:alipay", "REFUND_RESULT", "REFUND",
                refund.refundNo(), finalState, null, Map.of("orderNo", refund.orderNo(),
                        "amountMinor", refund.amountMinor(), "currency", "CNY"));
    }

    private String findOrderBuyerId(String orderNo) {
        return jdbcTemplate.queryForObject("SELECT buyer_id FROM commerce_order WHERE order_no = ?",
                String.class, orderNo);
    }

    @Transactional(readOnly = true)
    public List<String> findExpiredOrderNumbers(int limit) {
        if (limit < 1 || limit > MAX_EXPIRY_BATCH) {
            throw new IllegalArgumentException("Expiry scan limit is outside the configured bound");
        }
        return jdbcTemplate.query("SELECT o.order_no FROM commerce_order o "
                        + "WHERE o.order_state IN ('PENDING_PAYMENT', 'PAYMENT_CONFIRMING') "
                        + "AND o.payment_deadline <= UTC_TIMESTAMP(6) "
                        + "AND NOT EXISTS (SELECT 1 FROM commerce_compensation_task t "
                        + "WHERE t.task_key = CONCAT('EXPIRE:', o.order_no)) "
                        + "ORDER BY o.payment_deadline, o.order_id LIMIT ?",
                (rs, rowNum) -> rs.getString(1), limit);
    }

    @Transactional
    public boolean closeExpired(String orderNo) {
        validateOrderNo(orderNo);
        List<OrderLock> rows = jdbcTemplate.query("SELECT buyer_id, order_state, row_version, activity_id, sku_id, "
                        + "quantity, payment_deadline, total_amount_minor, currency FROM commerce_order "
                        + "WHERE order_no = ? AND order_state IN ('PENDING_PAYMENT', 'PAYMENT_CONFIRMING') "
                        + "AND payment_deadline <= UTC_TIMESTAMP(6) FOR UPDATE",
                (rs, rowNum) -> new OrderLock(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4),
                        rs.getLong(5), rs.getInt(6), rs.getObject(7, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getLong(8), rs.getString(9)), orderNo);
        if (rows.isEmpty()) {
            return false;
        }
        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM commerce_payment_attempt WHERE order_no = ?", Integer.class, orderNo);
        if (attempts != null && attempts > 0) {
            throw new IllegalStateException("Cannot close an expired order without checking Alipay");
        }
        releasePendingOrder(rows.getFirst(), orderNo, "system:expiry-closer", "EXPIRE:" + orderNo,
                "EXPIRE:" + orderNo,
                "Payment deadline expired", "PAYMENT_DEADLINE_EXPIRED");
        return true;
    }

    private void releasePendingOrder(OrderLock order, String orderNo, String actorId,
                                     String ledgerKey, String transitionKey,
                                     String reason, String eventReason) {
        ReservationLock reservation = lockReservation(orderNo);
        if (reservation == null || !"HELD".equals(reservation.state())
                || reservation.quantity() != order.quantity()) {
            throw new IllegalStateException("Pending order does not have its held inventory reservation");
        }
        InventoryLock inventory = lockInventory(order.activityId(), order.skuId());
        PurchaseQuota quota = lockQuota(order.buyerId(), order.activityId(), order.skuId());
        if (inventory == null || quota == null
                || inventory.reservedQuantity() < order.quantity()
                || quota.reservedQuantity() < order.quantity()) {
            throw new IllegalStateException("Held reservation is inconsistent with inventory or buyer quota");
        }
        ensureCanIncrement(order.rowVersion(), reservation.rowVersion(), inventory.rowVersion(), quota.rowVersion());

        int inventoryUpdated = jdbcTemplate.update("UPDATE commerce_inventory "
                        + "SET available_quantity = available_quantity + ?, "
                        + "reserved_quantity = reserved_quantity - ?, row_version = row_version + 1 "
                        + "WHERE activity_id = ? AND sku_id = ? AND row_version = ? "
                        + "AND reserved_quantity >= ? AND available_quantity + ? <= allocated_quantity",
                order.quantity(), order.quantity(), order.activityId(), order.skuId(), inventory.rowVersion(),
                order.quantity(), order.quantity());
        int quotaUpdated = jdbcTemplate.update("UPDATE commerce_purchase_quota "
                        + "SET reserved_quantity = reserved_quantity - ?, row_version = row_version + 1 "
                        + "WHERE buyer_id = ? AND activity_id = ? AND sku_id = ? AND row_version = ? "
                        + "AND reserved_quantity >= ?",
                order.quantity(), order.buyerId(), order.activityId(), order.skuId(), quota.rowVersion(), order.quantity());
        int reservationUpdated = jdbcTemplate.update("UPDATE commerce_reservation "
                        + "SET reservation_state = 'RELEASED', row_version = row_version + 1 "
                        + "WHERE order_no = ? AND reservation_state = 'HELD' AND row_version = ?",
                orderNo, reservation.rowVersion());
        int orderUpdated = jdbcTemplate.update("UPDATE commerce_order "
                        + "SET order_state = 'CLOSED', row_version = row_version + 1 "
                        + "WHERE order_no = ? AND order_state = ? AND row_version = ?",
                orderNo, order.state(), order.rowVersion());
        if (inventoryUpdated != 1 || quotaUpdated != 1 || reservationUpdated != 1 || orderUpdated != 1) {
            throw new IllegalStateException("Order close state changed after its rows were locked");
        }

        jdbcTemplate.update("INSERT INTO commerce_inventory_ledger "
                        + "(operation_key, activity_id, sku_id, order_no, actor_id, operation_type, "
                        + "available_delta, reserved_delta, sold_delta, reason) "
                        + "VALUES (?, ?, ?, ?, ?, 'ORDER_RESERVATION_RELEASE', ?, ?, 0, ?)",
                ledgerKey, order.activityId(), order.skuId(), orderNo, actorId,
                order.quantity(), -order.quantity(), reason);
        jdbcTemplate.update("INSERT INTO commerce_order_status_history "
                        + "(order_no, transition_key, from_state, to_state, actor_id, reason, row_version) "
                        + "VALUES (?, ?, ?, 'CLOSED', ?, ?, ?)",
                orderNo, transitionKey, order.state(), actorId, reason, order.rowVersion() + 1);
        jdbcTemplate.update("INSERT INTO commerce_outbox "
                        + "(event_id, aggregate_type, aggregate_id, event_type, event_version, payload) "
                        + "VALUES (?, 'ORDER', ?, 'OrderClosed', 2, ?)",
                UUID.randomUUID().toString(), orderNo,
                toJson(Map.of("orderNo", orderNo, "buyerId", order.buyerId(), "reason", eventReason)));
        TradeAuditWriter.record(jdbcTemplate, objectMapper, actorId, "ORDER_CLOSE", "ORDER", orderNo,
                "SUCCESS", eventReason, Map.of("quantity", order.quantity(), "fromState", order.state(),
                        "toState", "CLOSED"));
    }

    private void consumeReservedOrder(String orderNo, OrderLock order,
                                      TradePaymentProvider.ConfirmedPayment payment) {
        ReservationLock reservation = lockReservation(orderNo);
        InventoryLock inventory = lockInventory(order.activityId(), order.skuId());
        PurchaseQuota quota = lockQuota(order.buyerId(), order.activityId(), order.skuId());
        if (reservation == null || !"HELD".equals(reservation.state())
                || reservation.quantity() != order.quantity() || inventory == null || quota == null
                || inventory.reservedQuantity() < order.quantity() || quota.reservedQuantity() < order.quantity()) {
            throw new IllegalStateException("Paid order does not have consistent held inventory and quota");
        }
        ensureCanIncrement(order.rowVersion(), reservation.rowVersion(), inventory.rowVersion(), quota.rowVersion());

        int inventoryUpdated = jdbcTemplate.update("UPDATE commerce_inventory "
                        + "SET reserved_quantity = reserved_quantity - ?, sold_quantity = sold_quantity + ?, "
                        + "row_version = row_version + 1 WHERE activity_id = ? AND sku_id = ? AND row_version = ? "
                        + "AND reserved_quantity >= ?",
                order.quantity(), order.quantity(), order.activityId(), order.skuId(), inventory.rowVersion(),
                order.quantity());
        int quotaUpdated = jdbcTemplate.update("UPDATE commerce_purchase_quota "
                        + "SET reserved_quantity = reserved_quantity - ?, purchased_quantity = purchased_quantity + ?, "
                        + "row_version = row_version + 1 WHERE buyer_id = ? AND activity_id = ? AND sku_id = ? "
                        + "AND row_version = ? AND reserved_quantity >= ?",
                order.quantity(), order.quantity(), order.buyerId(), order.activityId(), order.skuId(),
                quota.rowVersion(), order.quantity());
        int reservationUpdated = jdbcTemplate.update("UPDATE commerce_reservation "
                        + "SET reservation_state = 'CONSUMED', row_version = row_version + 1 "
                        + "WHERE order_no = ? AND reservation_state = 'HELD' AND row_version = ?",
                orderNo, reservation.rowVersion());
        int orderUpdated = jdbcTemplate.update("UPDATE commerce_order SET order_state = 'PAID', "
                        + "row_version = row_version + 1 WHERE order_no = ? AND order_state = ? AND row_version = ?",
                orderNo, order.state(), order.rowVersion());
        int paymentUpdated = jdbcTemplate.update("UPDATE commerce_payment_attempt SET payment_state = 'SUCCEEDED', "
                        + "provider_trade_no = ?, paid_at = UTC_TIMESTAMP(6), row_version = row_version + 1 "
                        + "WHERE payment_no = ? AND payment_state <> 'SUCCEEDED'",
                payment.providerTradeNo(), payment.paymentNo());
        if (inventoryUpdated != 1 || quotaUpdated != 1 || reservationUpdated != 1
                || orderUpdated != 1 || paymentUpdated != 1) {
            throw new IllegalStateException("Paid order changed after its rows were locked");
        }

        String transitionKey = "PAY:" + orderNo;
        jdbcTemplate.update("INSERT INTO commerce_inventory_ledger "
                        + "(operation_key, activity_id, sku_id, order_no, actor_id, operation_type, "
                        + "available_delta, reserved_delta, sold_delta, reason) "
                        + "VALUES (?, ?, ?, ?, 'system:alipay', 'ORDER_PAYMENT', 0, ?, ?, ?)",
                "PAYMENT:" + payment.paymentNo(), order.activityId(), order.skuId(), orderNo,
                -order.quantity(), order.quantity(), "Alipay payment confirmed");
        jdbcTemplate.update("INSERT INTO commerce_order_status_history "
                        + "(order_no, transition_key, from_state, to_state, actor_id, reason, row_version) "
                        + "VALUES (?, ?, ?, 'PAID', 'system:alipay', ?, ?)",
                orderNo, transitionKey, order.state(), "Alipay payment confirmed", order.rowVersion() + 1);
        jdbcTemplate.update("INSERT INTO commerce_outbox "
                        + "(event_id, aggregate_type, aggregate_id, event_type, event_version, payload) "
                        + "VALUES (?, 'ORDER', ?, 'OrderPaid', 1, ?)",
                UUID.randomUUID().toString(), orderNo, toJson(Map.of(
                        "orderNo", orderNo, "buyerId", order.buyerId(), "paymentNo", payment.paymentNo(),
                        "providerTradeNo", payment.providerTradeNo(), "amountMinor", payment.amountMinor(),
                        "currency", order.currency())));
    }

    private void enqueueLatePaymentRefund(String orderNo, OrderLock order,
                                          TradePaymentProvider.ConfirmedPayment payment) {
        String refundNo = "RF" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String idempotencyKey = "LATEPAY:" + payment.paymentNo();
        int inserted = jdbcTemplate.update("INSERT IGNORE INTO commerce_refund "
                        + "(refund_no, order_no, idempotency_key, amount_minor, currency, refund_state, "
                        + "requested_by, reason, row_version) VALUES (?, ?, ?, ?, 'CNY', 'PENDING', ?, ?, 0)",
                refundNo, orderNo, idempotencyKey, payment.amountMinor(), "system:payment-reconcile",
                "Late Alipay payment after order closure; full refund required");
        if (inserted == 0) {
            return;
        }
        jdbcTemplate.update("INSERT IGNORE INTO commerce_compensation_task "
                        + "(task_key, task_type, aggregate_id, task_state, available_at) "
                        + "VALUES (?, 'PROCESS_REFUND', ?, 'PENDING', UTC_TIMESTAMP(6))",
                "REFUND:" + refundNo, orderNo);
    }

    private String findOrderSubject(String orderNo) {
        return jdbcTemplate.queryForObject("SELECT product_name_snapshot FROM commerce_order WHERE order_no = ?",
                String.class, orderNo);
    }

    private String findPaymentOrder(String paymentNo) {
        List<String> rows = jdbcTemplate.query("SELECT order_no FROM commerce_payment_attempt WHERE payment_no = ?",
                (rs, rowNum) -> rs.getString(1), paymentNo);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private OrderLock lockOrderForPayment(String orderNo) {
        List<OrderLock> rows = jdbcTemplate.query("SELECT buyer_id, order_state, row_version, activity_id, sku_id, "
                        + "quantity, payment_deadline, total_amount_minor, currency FROM commerce_order "
                        + "WHERE order_no = ? FOR UPDATE",
                (rs, rowNum) -> readOrderLock(rs), orderNo);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private PaymentAttemptLock lockPaymentAttempt(String paymentNo) {
        List<PaymentAttemptLock> rows = jdbcTemplate.query("SELECT payment_no, order_no, provider, amount_minor, "
                        + "payment_state, provider_trade_no FROM commerce_payment_attempt WHERE payment_no = ? FOR UPDATE",
                (rs, rowNum) -> new PaymentAttemptLock(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getString(5), rs.getString(6)), paymentNo);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static OrderLock readOrderLock(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OrderLock(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5),
                rs.getInt(6), rs.getObject(7, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getLong(8), rs.getString(9));
    }

    private TradeOrderOutcome replayCancellation(String buyerId, String orderNo, String key, String requestHash) {
        List<IdempotencyRecord> rows = jdbcTemplate.query(
                "SELECT request_hash, request_state, result_order_no, result_code "
                        + "FROM commerce_idempotency_request "
                        + "WHERE buyer_id = ? AND operation_scope = ? AND idempotency_key = ? FOR UPDATE",
                (rs, rowNum) -> new IdempotencyRecord(rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4)), buyerId, CANCEL_SCOPE, key);
        if (rows.isEmpty()) {
            throw new IllegalStateException("Cancellation idempotency row disappeared after duplicate insert");
        }
        IdempotencyRecord record = rows.getFirst();
        if (!MessageDigest.isEqual(record.requestHash().getBytes(StandardCharsets.US_ASCII),
                requestHash.getBytes(StandardCharsets.US_ASCII))) {
            return TradeOrderOutcome.rejected("IDEMPOTENCY_CONFLICT", 409);
        }
        if ("REJECTED".equals(record.state())) {
            String errorCode = record.resultCode() == null ? "ORDER_STATE_CONFLICT" : record.resultCode();
            return TradeOrderOutcome.rejected(errorCode, statusFor(errorCode));
        }
        if (!"COMPLETED".equals(record.state()) || !orderNo.equals(record.orderNo())) {
            return TradeOrderOutcome.rejected("IDEMPOTENCY_IN_PROGRESS", 409);
        }
        OrderLock order = lockOrder(buyerId, orderNo);
        if (order == null) {
            throw new IllegalStateException("Completed cancellation references a missing owned order");
        }
        return TradeOrderOutcome.success(new TradeOrderResponse(orderNo, order.state(), order.paymentDeadline(),
                order.totalAmountMinor(), order.currency(), true));
    }

    private OrderLock lockOrder(String buyerId, String orderNo) {
        List<OrderLock> rows = jdbcTemplate.query("SELECT buyer_id, order_state, row_version, activity_id, sku_id, quantity, "
                        + "payment_deadline, total_amount_minor, currency FROM commerce_order "
                        + "WHERE order_no = ? AND buyer_id = ? FOR UPDATE",
                (rs, rowNum) -> new OrderLock(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4),
                        rs.getLong(5), rs.getInt(6), rs.getObject(7, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getLong(8), rs.getString(9)), orderNo, buyerId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private ReservationLock lockReservation(String orderNo) {
        List<ReservationLock> rows = jdbcTemplate.query("SELECT quantity, reservation_state, row_version "
                        + "FROM commerce_reservation WHERE order_no = ? FOR UPDATE",
                (rs, rowNum) -> new ReservationLock(rs.getInt(1), rs.getString(2), rs.getLong(3)), orderNo);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private InventoryLock lockInventory(long activityId, long skuId) {
        List<InventoryLock> rows = jdbcTemplate.query("SELECT reserved_quantity, row_version FROM commerce_inventory "
                        + "WHERE activity_id = ? AND sku_id = ? FOR UPDATE",
                (rs, rowNum) -> new InventoryLock(rs.getLong(1), rs.getLong(2)), activityId, skuId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private PurchaseQuota lockQuota(String buyerId, long activityId, long skuId) {
        List<PurchaseQuota> rows = jdbcTemplate.query("SELECT reserved_quantity, purchased_quantity, row_version "
                        + "FROM commerce_purchase_quota WHERE buyer_id = ? AND activity_id = ? AND sku_id = ? FOR UPDATE",
                (rs, rowNum) -> new PurchaseQuota(rs.getLong(1), rs.getLong(2), rs.getLong(3)),
                buyerId, activityId, skuId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private TradeOrderOutcome reject(String buyerId, String key, String errorCode, int status) {
        jdbcTemplate.update("UPDATE commerce_idempotency_request SET request_state = 'REJECTED', result_code = ? "
                        + "WHERE buyer_id = ? AND operation_scope = ? AND idempotency_key = ?",
                errorCode, buyerId, CANCEL_SCOPE, key);
        return TradeOrderOutcome.rejected(errorCode, status);
    }

    private CreateTradeOrderRequest.ShippingAddress readShippingAddress(String json) {
        try {
            return objectMapper.readValue(json, CreateTradeOrderRequest.ShippingAddress.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Stored shipping snapshot is invalid", error);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize an order event", error);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private static void validateBuyer(String buyerId) {
        if (!StringUtils.hasText(buyerId) || buyerId.length() > 64) {
            throw new TradeOrderApiException(401, "UNAUTHENTICATED");
        }
    }

    private static void validateOrderNo(String orderNo) {
        if (orderNo == null || !ORDER_NO.matcher(orderNo).matches()) {
            throw new TradeOrderApiException(400, "VALIDATION_FAILED");
        }
    }

    private static int statusFor(String errorCode) {
        return "ORDER_NOT_FOUND".equals(errorCode) ? 404 : 409;
    }

    private static void ensureCanIncrement(long... versions) {
        for (long version : versions) {
            if (version < 0 || version == Long.MAX_VALUE) {
                throw new IllegalStateException("Persisted row version cannot be incremented safely");
            }
        }
    }

    private record OrderLock(String buyerId, String state, long rowVersion, long activityId, long skuId, int quantity,
                             Instant paymentDeadline, long totalAmountMinor, String currency) {
    }

    private record ReservationLock(int quantity, String state, long rowVersion) {
    }

    private record InventoryLock(long reservedQuantity, long rowVersion) {
    }

    private record PurchaseQuota(long reservedQuantity, long purchasedQuantity, long rowVersion) {
    }

    private record PaymentAttemptLock(String paymentNo, String orderNo, String provider, long amountMinor,
                                      String state, String providerTradeNo) {
        private PaymentAttemptLock(String paymentNo, long amountMinor, String state) {
            this(paymentNo, null, "ALIPAY", amountMinor, state, null);
        }
    }

    public record PaymentCheckoutDetails(String paymentNo, String subject, long amountMinor,
                                         long remainingSeconds, String mode) {
    }

    public record PaymentCheck(String orderNo, String paymentNo, long amountMinor) {
    }

    public record RefundPlan(String refundNo, String orderNo, String paymentNo, long amountMinor,
                             String reason, String state, String orderState) {
    }

    private record ExistingRefund(RefundPlan plan, String requestedBy, String idempotencyKey) {
    }

    private record RefundableOrder(String state, String fulfillmentState, long rowVersion, long amountMinor) {
    }

    private record IdempotencyRecord(String requestHash, String state, String orderNo, String resultCode) {
    }
}

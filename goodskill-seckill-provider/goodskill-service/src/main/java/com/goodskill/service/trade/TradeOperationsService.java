package com.goodskill.service.trade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Profile("production")
public class TradeOperationsService {
    private static final Pattern ORDER_NO = Pattern.compile("GS[0-9A-F]{32}");
    private static final List<String> ORDER_STATES = List.of(
            "PENDING_PAYMENT", "PAYMENT_CONFIRMING", "PAID", "CLOSED", "REFUNDING", "REFUNDED");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TradeOperationsService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public TradeManagedOrderPageResponse listOrders(String state, long page, int size) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 100
                || (state != null && !ORDER_STATES.contains(state))) {
            throw new TradeOrderApiException(400, "VALIDATION_FAILED");
        }
        String where = state == null ? "" : " WHERE order_state = ?";
        Long total = state == null
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM commerce_order", Long.class)
                : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM commerce_order WHERE order_state = ?", Long.class, state);
        long offset = Math.multiplyExact(page - 1, size);
        List<TradeManagedOrderResponse> orders = state == null
                ? jdbcTemplate.query(orderSelect() + " ORDER BY created_at DESC, order_id DESC LIMIT ? OFFSET ?",
                    (rs, rowNum) -> mapOrder(rs), size, offset)
                : jdbcTemplate.query(orderSelect() + where + " ORDER BY created_at DESC, order_id DESC LIMIT ? OFFSET ?",
                    (rs, rowNum) -> mapOrder(rs), state, size, offset);
        return new TradeManagedOrderPageResponse(page, size, total == null ? 0 : total, orders);
    }

    @Transactional(readOnly = true)
    public TradeOperationsMetrics metrics() {
        return jdbcTemplate.queryForObject("SELECT "
                        + "(SELECT COUNT(*) FROM commerce_activity WHERE state = 'PUBLISHED' "
                        + "AND starts_at <= UTC_TIMESTAMP(6) AND ends_at > UTC_TIMESTAMP(6)) AS active_activities, "
                        + "(SELECT COUNT(*) FROM commerce_order WHERE order_state = 'PENDING_PAYMENT') AS pending_orders, "
                        + "(SELECT COUNT(*) FROM commerce_order WHERE order_state = 'PAID') AS paid_orders, "
                        + "(SELECT COUNT(*) FROM commerce_order WHERE order_state = 'PAID' "
                        + "AND fulfillment_state IN ('UNFULFILLED', 'PACKING')) AS waiting_fulfillment, "
                        + "(SELECT COALESCE(SUM(reserved_quantity), 0) FROM commerce_inventory) AS reserved_units, "
                        + "(SELECT COALESCE(SUM(i.available_quantity), 0) FROM commerce_inventory i "
                        + "JOIN commerce_activity a ON a.activity_id = i.activity_id "
                        + "WHERE a.state = 'PUBLISHED' AND a.starts_at <= UTC_TIMESTAMP(6) "
                        + "AND a.ends_at > UTC_TIMESTAMP(6)) AS available_units",
                (rs, rowNum) -> new TradeOperationsMetrics(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getLong(4), rs.getLong(5), rs.getLong(6)));
    }

    @Transactional(readOnly = true)
    public TradeManagedOrderDetailResponse orderDetail(String orderNo) {
        validateOrderNo(orderNo);
        List<TradeManagedOrderResponse> rows = jdbcTemplate.query(orderSelect() + " WHERE order_no = ?",
                (rs, rowNum) -> mapOrder(rs), orderNo);
        if (rows.isEmpty()) throw new TradeOrderApiException(404, "ORDER_NOT_FOUND");
        String addressJson = jdbcTemplate.queryForObject(
                "SELECT shipping_snapshot FROM commerce_order WHERE order_no = ?", String.class, orderNo);
        List<FulfillmentRow> fulfillment = jdbcTemplate.query(
                "SELECT carrier_code, tracking_no FROM commerce_fulfillment WHERE order_no = ?",
                (rs, rowNum) -> new FulfillmentRow(rs.getString(1), rs.getString(2)), orderNo);
        List<String> refunds = jdbcTemplate.query("SELECT refund_state FROM commerce_refund WHERE order_no = ?",
                (rs, rowNum) -> rs.getString(1), orderNo);
        FulfillmentRow shipment = fulfillment.isEmpty() ? new FulfillmentRow(null, null) : fulfillment.getFirst();
        return new TradeManagedOrderDetailResponse(rows.getFirst(), parseAddress(addressJson), shipment.carrierCode(),
                shipment.trackingNo(), refunds.isEmpty() ? null : refunds.getFirst());
    }

    @Transactional
    public TradeManagedOrderDetailResponse ship(String actorId, String orderNo, TradeShipmentRequest request) {
        validateActor(actorId);
        validateOrderNo(orderNo);
        if (request == null || !StringUtils.hasText(request.carrierCode()) || !StringUtils.hasText(request.trackingNo())) {
            throw new TradeOrderApiException(400, "VALIDATION_FAILED");
        }
        List<ShipmentOrderLock> locked = jdbcTemplate.query(
                "SELECT buyer_id, order_state, fulfillment_state, shipping_snapshot, row_version "
                        + "FROM commerce_order WHERE order_no = ? FOR UPDATE",
                (rs, rowNum) -> new ShipmentOrderLock(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5)), orderNo);
        if (locked.isEmpty()) throw new TradeOrderApiException(404, "ORDER_NOT_FOUND");
        ShipmentOrderLock order = locked.getFirst();
        List<FulfillmentRow> existing = jdbcTemplate.query(
                "SELECT carrier_code, tracking_no FROM commerce_fulfillment WHERE order_no = ? FOR UPDATE",
                (rs, rowNum) -> new FulfillmentRow(rs.getString(1), rs.getString(2)), orderNo);
        if (!existing.isEmpty()) {
            FulfillmentRow saved = existing.getFirst();
            if ("SHIPPED".equals(order.fulfillmentState()) && request.carrierCode().equals(saved.carrierCode())
                    && request.trackingNo().equals(saved.trackingNo())) return orderDetail(orderNo);
            throw new TradeOrderApiException(409, "FULFILLMENT_STATE_CONFLICT");
        }
        if (!"PAID".equals(order.orderState()) || !"UNFULFILLED".equals(order.fulfillmentState())) {
            throw new TradeOrderApiException(409, "FULFILLMENT_STATE_CONFLICT");
        }
        if (order.rowVersion() < 0 || order.rowVersion() == Long.MAX_VALUE) {
            throw new IllegalStateException("Persisted order row version cannot be incremented safely");
        }
        CreateTradeOrderRequest.ShippingAddress address = parseAddress(order.shippingSnapshot());
        jdbcTemplate.update("INSERT INTO commerce_fulfillment "
                        + "(order_no, state, recipient_name, recipient_phone, address_snapshot, carrier_code, "
                        + "tracking_no, shipped_at, row_version) VALUES (?, 'SHIPPED', ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), 0)",
                orderNo, address.recipientName(), address.recipientPhone(), order.shippingSnapshot(),
                request.carrierCode(), request.trackingNo());
        int changed = jdbcTemplate.update("UPDATE commerce_order SET fulfillment_state = 'SHIPPED', "
                        + "row_version = row_version + 1 WHERE order_no = ? AND order_state = 'PAID' "
                        + "AND fulfillment_state = 'UNFULFILLED' AND row_version = ?",
                orderNo, order.rowVersion());
        if (changed != 1) throw new TradeOrderApiException(409, "FULFILLMENT_STATE_CONFLICT");
        TradeAuditWriter.record(jdbcTemplate, objectMapper, actorId, "FULFILLMENT_SHIP", "ORDER", orderNo,
                "SUCCESS", null, Map.of("carrierCode", request.carrierCode(), "trackingNo", request.trackingNo()));
        return orderDetail(orderNo);
    }

    private String orderSelect() {
        return "SELECT order_no, buyer_id, activity_id, product_name_snapshot, sku_name_snapshot, quantity, "
                + "unit_price_minor, total_amount_minor, currency, order_state, fulfillment_state, "
                + "payment_deadline, created_at FROM commerce_order";
    }

    private static TradeManagedOrderResponse mapOrder(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TradeManagedOrderResponse(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                rs.getString(5), rs.getInt(6), rs.getLong(7), rs.getLong(8), rs.getString(9), rs.getString(10),
                rs.getString(11), rs.getObject(12, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getObject(13, LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

    private CreateTradeOrderRequest.ShippingAddress parseAddress(String json) {
        try {
            return objectMapper.readValue(json, CreateTradeOrderRequest.ShippingAddress.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Stored shipping snapshot is invalid", error);
        }
    }

    private static void validateActor(String actorId) {
        if (!StringUtils.hasText(actorId) || actorId.length() > 64) {
            throw new TradeOrderApiException(401, "UNAUTHENTICATED");
        }
    }

    private static void validateOrderNo(String orderNo) {
        if (orderNo == null || !ORDER_NO.matcher(orderNo).matches()) {
            throw new TradeOrderApiException(400, "VALIDATION_FAILED");
        }
    }

    private record FulfillmentRow(String carrierCode, String trackingNo) {}
    private record ShipmentOrderLock(String buyerId, String orderState, String fulfillmentState,
                                     String shippingSnapshot, long rowVersion) {}
}

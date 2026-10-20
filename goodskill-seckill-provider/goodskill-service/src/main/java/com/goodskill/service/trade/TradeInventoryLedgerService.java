package com.goodskill.service.trade;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Profile("production")
public class TradeInventoryLedgerService {
    private static final Pattern ORDER_NO = Pattern.compile("GS[0-9A-F]{32}");
    private final JdbcTemplate jdbcTemplate;

    public TradeInventoryLedgerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public TradeInventoryLedgerPageResponse list(Long activityId, Long skuId, String orderNo, long page, int size) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 100
                || (activityId != null && activityId <= 0) || (skuId != null && skuId <= 0)
                || (orderNo != null && !ORDER_NO.matcher(orderNo).matches())
                || (activityId == null && skuId == null && orderNo == null)) {
            throw new TradeOrderApiException(400, "VALIDATION_FAILED");
        }
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (activityId != null) { where.append(" AND activity_id = ?"); parameters.add(activityId); }
        if (skuId != null) { where.append(" AND sku_id = ?"); parameters.add(skuId); }
        if (orderNo != null) { where.append(" AND order_no = ?"); parameters.add(orderNo); }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM commerce_inventory_ledger" + where,
                Long.class, parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add(Math.multiplyExact(page - 1, size));
        List<TradeInventoryLedgerRecord> records = jdbcTemplate.query(
                "SELECT ledger_id, activity_id, sku_id, order_no, actor_id, operation_type, available_delta, "
                        + "reserved_delta, sold_delta, reason, created_at FROM commerce_inventory_ledger" + where
                        + " ORDER BY created_at DESC, ledger_id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new TradeInventoryLedgerRecord(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getLong(7), rs.getLong(8),
                        rs.getLong(9), rs.getString(10), rs.getObject(11, java.time.LocalDateTime.class)),
                pageParameters.toArray());
        return new TradeInventoryLedgerPageResponse(page, size, total == null ? 0 : total, records);
    }
}

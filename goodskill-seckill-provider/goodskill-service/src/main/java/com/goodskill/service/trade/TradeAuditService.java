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
public class TradeAuditService {

    private static final Pattern FILTER_VALUE = Pattern.compile("[A-Za-z0-9._:-]{1,80}");
    private final JdbcTemplate jdbcTemplate;

    public TradeAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public TradeAuditPageResponse list(String actorFilter, String action, String targetType, String targetId,
                                       long page, int size) {
        if ((actorFilter != null && (actorFilter.length() > 64 || !FILTER_VALUE.matcher(actorFilter).matches()))
                || page < 1 || page > 1_000_000 || size < 1 || size > 100
                || !validFilter(action) || !validFilter(targetType) || !validFilter(targetId)
                || (actorFilter == null && action == null && targetType == null && targetId == null)) {
            throw new TradeOrderApiException(400, "VALIDATION_FAILED");
        }
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (action != null) {
            where.append(" AND action = ?");
            parameters.add(action);
        }
        if (targetType != null) {
            where.append(" AND target_type = ?");
            parameters.add(targetType);
        }
        if (targetId != null) {
            where.append(" AND target_id = ?");
            parameters.add(targetId);
        }
        if (actorFilter != null) {
            where.append(" AND actor_id = ?");
            parameters.add(actorFilter);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM commerce_audit_event" + where,
                Long.class, parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add(Math.multiplyExact(page - 1, size));
        List<TradeAuditRecord> records = jdbcTemplate.query("SELECT audit_id, actor_id, action, target_type, "
                        + "target_id, request_id, result, reason, changes, created_at FROM commerce_audit_event"
                        + where + " ORDER BY created_at DESC, audit_id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new TradeAuditRecord(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getObject(10, java.time.LocalDateTime.class)), pageParameters.toArray());
        return new TradeAuditPageResponse(page, size, total == null ? 0 : total, records);
    }

    private static boolean validFilter(String value) {
        return value == null || FILTER_VALUE.matcher(value).matches();
    }
}

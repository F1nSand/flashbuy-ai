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
public class TradeOutboxStore {

    public static final int MAX_ATTEMPTS = 20;
    private static final int MAX_BATCH_SIZE = 100;
    private static final Pattern WORKER_ID = Pattern.compile("[A-Za-z0-9._:-]{1,80}");

    private final JdbcTemplate jdbcTemplate;

    public TradeOutboxStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<TradeOutboxEvent> claim(String workerId, int limit, int leaseSeconds) {
        validate(workerId, limit, leaseSeconds);
        jdbcTemplate.update("UPDATE commerce_outbox SET publish_state = 'DEAD', "
                        + "last_error_code = 'LEASE_EXPIRED_AFTER_MAX_ATTEMPTS', lease_owner = NULL, lease_until = NULL "
                        + "WHERE publish_state = 'PUBLISHING' AND lease_until <= UTC_TIMESTAMP(6) "
                        + "AND attempt_count >= ?",
                MAX_ATTEMPTS);
        List<TradeOutboxEvent> events = jdbcTemplate.query("SELECT event_id, aggregate_type, aggregate_id, "
                        + "event_type, event_version, payload, attempt_count FROM commerce_outbox "
                        + "WHERE attempt_count < ? AND ((publish_state = 'PENDING' AND available_at <= UTC_TIMESTAMP(6)) "
                        + "OR (publish_state = 'PUBLISHING' AND lease_until <= UTC_TIMESTAMP(6))) "
                        + "ORDER BY available_at, created_at, event_id LIMIT ? FOR UPDATE SKIP LOCKED",
                (rs, rowNum) -> new TradeOutboxEvent(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getInt(5), rs.getString(6), rs.getInt(7) + 1),
                MAX_ATTEMPTS, limit);

        List<TradeOutboxEvent> claimed = new ArrayList<>(events.size());
        for (TradeOutboxEvent event : events) {
            int updated = jdbcTemplate.update("UPDATE commerce_outbox SET publish_state = 'PUBLISHING', "
                            + "attempt_count = attempt_count + 1, lease_owner = ?, "
                            + "lease_until = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL ? SECOND), last_error_code = NULL "
                            + "WHERE event_id = ? AND attempt_count < ?",
                    workerId, leaseSeconds, event.eventId(), MAX_ATTEMPTS);
            if (updated != 1) {
                throw new IllegalStateException("Outbox event changed while its row lock was held");
            }
            claimed.add(event);
        }
        return claimed;
    }

    @Transactional
    public void markPublished(String eventId, String workerId) {
        int updated = jdbcTemplate.update("UPDATE commerce_outbox SET publish_state = 'PUBLISHED', "
                        + "published_at = UTC_TIMESTAMP(6), lease_owner = NULL, lease_until = NULL "
                        + "WHERE event_id = ? AND publish_state = 'PUBLISHING' AND lease_owner = ?",
                eventId, workerId);
        if (updated != 1) {
            throw new IllegalStateException("Outbox publish lease was lost before completion");
        }
    }

    @Transactional
    public boolean markFailed(TradeOutboxEvent event, String workerId, String errorCode, int retryDelaySeconds) {
        if (errorCode == null || errorCode.isBlank() || errorCode.length() > 80
                || retryDelaySeconds < 1 || retryDelaySeconds > 3600) {
            throw new IllegalArgumentException("Invalid outbox failure metadata");
        }
        int updated = jdbcTemplate.update("UPDATE commerce_outbox SET publish_state = "
                        + "CASE WHEN attempt_count >= ? THEN 'DEAD' ELSE 'PENDING' END, "
                        + "available_at = CASE WHEN attempt_count >= ? THEN available_at "
                        + "ELSE DATE_ADD(UTC_TIMESTAMP(6), INTERVAL ? SECOND) END, "
                        + "last_error_code = ?, lease_owner = NULL, lease_until = NULL "
                        + "WHERE event_id = ? AND publish_state = 'PUBLISHING' AND lease_owner = ?",
                MAX_ATTEMPTS, MAX_ATTEMPTS, retryDelaySeconds, errorCode, event.eventId(), workerId);
        if (updated != 1) {
            throw new IllegalStateException("Outbox publish lease was lost before failure was recorded");
        }
        return event.attemptCount() >= MAX_ATTEMPTS;
    }

    private static void validate(String workerId, int limit, int leaseSeconds) {
        if (workerId == null || !WORKER_ID.matcher(workerId).matches() || limit < 1 || limit > MAX_BATCH_SIZE
                || leaseSeconds < 5 || leaseSeconds > 3600) {
            throw new IllegalArgumentException("Outbox claim parameters are outside the supported bound");
        }
    }
}

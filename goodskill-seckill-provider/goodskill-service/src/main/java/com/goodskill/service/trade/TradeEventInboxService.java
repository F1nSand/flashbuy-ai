package com.goodskill.service.trade;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@Profile("production")
public class TradeEventInboxService {

    public static final String CONSUMER_NAME = "trade-event-inbox-v1";
    private static final Pattern EVENT_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern EVENT_TYPE = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,79}");

    private final JdbcTemplate jdbcTemplate;

    public TradeEventInboxService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public boolean persist(TradeEventEnvelope event) {
        validate(event);
        return jdbcTemplate.update("INSERT IGNORE INTO commerce_event_inbox "
                        + "(consumer_name, event_id, aggregate_type, aggregate_id, event_type, event_version, "
                        + "payload, event_state, available_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'RECEIVED', UTC_TIMESTAMP(6))",
                CONSUMER_NAME, event.eventId(), event.aggregateType(), event.aggregateId(), event.eventType(),
                event.eventVersion(), event.payload()) == 1;
    }

    private static void validate(TradeEventEnvelope event) {
        if (event == null || event.eventId() == null || !EVENT_ID.matcher(event.eventId()).matches()
                || event.aggregateType() == null || event.aggregateType().isBlank()
                || event.aggregateType().length() > 40 || event.aggregateId() == null
                || event.aggregateId().isBlank() || event.aggregateId().length() > 64
                || event.eventType() == null || !EVENT_TYPE.matcher(event.eventType()).matches()
                || event.eventVersion() < 1 || event.payload() == null || event.payload().isBlank()) {
            throw new IllegalArgumentException("Malformed commerce event envelope");
        }
    }
}

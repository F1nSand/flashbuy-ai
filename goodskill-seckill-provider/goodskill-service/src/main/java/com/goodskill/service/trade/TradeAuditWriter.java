package com.goodskill.service.trade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

final class TradeAuditWriter {

    private TradeAuditWriter() {
    }

    static void record(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, String actorId, String action,
                       String targetType, String targetId, String result, String reason,
                       Map<String, ?> changes) {
        String changesJson;
        try {
            changesJson = changes == null ? null : objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException serializationFailure) {
            throw new IllegalStateException("Could not serialize an audit record", serializationFailure);
        }
        jdbcTemplate.update("INSERT INTO commerce_audit_event "
                        + "(actor_id, action, target_type, target_id, request_id, result, reason, changes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                actorId, action, targetType, targetId, UUID.randomUUID().toString(), result, reason, changesJson);
    }
}

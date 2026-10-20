package com.goodskill.service.trade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("production")
public class ActivityService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ActivityService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ActivityResponse create(String actorId, CreateActivityRequest request) {
        validateActor(actorId);
        if (request == null || !StringUtils.hasText(request.activityCode()) || request.activityCode().length() > 64
                || !StringUtils.hasText(request.title()) || request.title().length() > 200
                || request.skus() == null || request.skus().isEmpty() || request.skus().size() > 100
                || request.startsAt() == null || request.endsAt() == null
                || !request.endsAt().isAfter(request.startsAt())) {
            throw new ActivityApiException(400, "VALIDATION_FAILED");
        }
        String activityCode = request.activityCode().trim();
        String title = request.title().trim();

        long activityId = insertAndGetId(
                "INSERT INTO commerce_activity "
                        + "(activity_code, title, state, starts_at, ends_at, published_version, row_version, "
                        + "created_by, updated_by) VALUES (?, ?, 'DRAFT', ?, ?, 0, 0, ?, ?)",
                activityCode, title, utc(request.startsAt()), utc(request.endsAt()), actorId, actorId);

        List<ActivitySkuResponse> skuResponses = new ArrayList<>();
        for (CreateActivityRequest.ActivitySku sku : request.skus()) {
            validateSku(sku);
            long skuId = insertAndGetId(
                    "INSERT INTO commerce_sku "
                            + "(sku_code, product_name, sku_name, status, list_price_minor, currency, row_version) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?, 'CNY', 0)",
                    sku.skuCode(), sku.productName(), sku.skuName(), sku.unitPriceMinor());
            jdbcTemplate.update("INSERT INTO commerce_activity_sku "
                            + "(activity_id, sku_id, product_name_snapshot, sku_name_snapshot, unit_price_minor, "
                            + "currency, purchase_limit_per_user, row_version) "
                            + "VALUES (?, ?, ?, ?, ?, 'CNY', ?, 0)",
                    activityId, skuId, sku.productName(), sku.skuName(), sku.unitPriceMinor(),
                    sku.purchaseLimitPerUser());
            jdbcTemplate.update("INSERT INTO commerce_inventory "
                            + "(activity_id, sku_id, allocated_quantity, available_quantity, reserved_quantity, "
                            + "sold_quantity, row_version) VALUES (?, ?, ?, ?, 0, 0, 0)",
                    activityId, skuId, sku.allocatedQuantity(), sku.allocatedQuantity());
            skuResponses.add(new ActivitySkuResponse(skuId, sku.skuCode(), sku.productName(), sku.skuName(),
                    sku.unitPriceMinor(), sku.purchaseLimitPerUser(), true,
                    sku.allocatedQuantity(), sku.allocatedQuantity()));
        }

        audit(actorId, "ACTIVITY_CREATE", activityId,
                Map.of("activityCode", activityCode, "skuCount", request.skus().size()));
        return new ActivityResponse(activityId, activityCode, title, "DRAFT",
                request.startsAt(), request.endsAt(), 0, 0, List.copyOf(skuResponses));
    }

    @Transactional
    public ActivityResponse updateDraft(long activityId, String actorId, UpdateActivityRequest request) {
        validateActor(actorId);
        if (activityId <= 0 || request == null || request.expectedVersion() == null
                || request.expectedVersion() < 0) {
            throw new ActivityApiException(400, "VALIDATION_FAILED");
        }
        ActivityRow current = lockActivity(activityId);
        if (!"DRAFT".equals(current.state())) {
            throw new ActivityApiException(409, "ACTIVITY_STATE_CONFLICT");
        }
        if (current.rowVersion() != request.expectedVersion()) {
            throw new ActivityApiException(409, "VERSION_CONFLICT");
        }

        String title = request.title() == null ? current.title() : request.title().trim();
        Instant startsAt = request.startsAt() == null ? current.startsAt() : request.startsAt();
        Instant endsAt = request.endsAt() == null ? current.endsAt() : request.endsAt();
        if (!StringUtils.hasText(title) || title.length() > 200 || !endsAt.isAfter(startsAt)) {
            throw new ActivityApiException(400, "VALIDATION_FAILED");
        }

        int updated = jdbcTemplate.update("UPDATE commerce_activity SET title = ?, starts_at = ?, ends_at = ?, "
                        + "updated_by = ?, row_version = row_version + 1 "
                        + "WHERE activity_id = ? AND state = 'DRAFT' AND row_version = ?",
                title, utc(startsAt), utc(endsAt), actorId, activityId, request.expectedVersion());
        if (updated != 1) {
            throw new ActivityApiException(409, "VERSION_CONFLICT");
        }
        audit(actorId, "ACTIVITY_UPDATE", activityId, Map.of("rowVersion", request.expectedVersion() + 1));
        return activityById(activityId);
    }

    @Transactional
    public ActivityResponse publish(long activityId, String actorId, long expectedVersion) {
        validateActor(actorId);
        if (activityId <= 0 || expectedVersion < 0) {
            throw new ActivityApiException(400, "VALIDATION_FAILED");
        }
        ActivityRow current = lockActivity(activityId);
        if (!"DRAFT".equals(current.state())) {
            throw new ActivityApiException(409, "ACTIVITY_STATE_CONFLICT");
        }
        if (current.rowVersion() != expectedVersion) {
            throw new ActivityApiException(409, "VERSION_CONFLICT");
        }
        Long skuCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM commerce_activity_sku WHERE activity_id = ?", Long.class, activityId);
        Long inventoryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM commerce_activity_sku s JOIN commerce_inventory i "
                        + "ON i.activity_id = s.activity_id AND i.sku_id = s.sku_id WHERE s.activity_id = ?",
                Long.class, activityId);
        if (skuCount == null || skuCount == 0 || !skuCount.equals(inventoryCount)) {
            throw new ActivityApiException(409, "ACTIVITY_INVENTORY_INCOMPLETE");
        }

        int updated = jdbcTemplate.update("UPDATE commerce_activity SET state = 'PUBLISHED', "
                        + "published_version = published_version + 1, row_version = row_version + 1, updated_by = ? "
                        + "WHERE activity_id = ? AND state = 'DRAFT' AND row_version = ?",
                actorId, activityId, expectedVersion);
        if (updated != 1) {
            throw new ActivityApiException(409, "VERSION_CONFLICT");
        }
        audit(actorId, "ACTIVITY_PUBLISH", activityId,
                Map.of("publishedVersion", current.publishedVersion() + 1));
        return activityById(activityId);
    }

    @Transactional
    public ActivityResponse pause(long activityId, String actorId, long expectedVersion) {
        validateActor(actorId);
        if (activityId <= 0 || expectedVersion < 0) {
            throw new ActivityApiException(400, "VALIDATION_FAILED");
        }
        ActivityRow current = lockActivity(activityId);
        if (!"PUBLISHED".equals(current.state())) {
            throw new ActivityApiException(409, "ACTIVITY_STATE_CONFLICT");
        }
        if (current.rowVersion() != expectedVersion) {
            throw new ActivityApiException(409, "VERSION_CONFLICT");
        }
        int updated = jdbcTemplate.update("UPDATE commerce_activity SET state = 'PAUSED', "
                        + "row_version = row_version + 1, updated_by = ? "
                        + "WHERE activity_id = ? AND state = 'PUBLISHED' AND row_version = ?",
                actorId, activityId, expectedVersion);
        if (updated != 1) {
            throw new ActivityApiException(409, "VERSION_CONFLICT");
        }
        audit(actorId, "ACTIVITY_PAUSE", activityId, Map.of("rowVersion", expectedVersion + 1));
        return activityById(activityId);
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> publicActivities(int limit) {
        if (limit < 1 || limit > 100) {
            throw new ActivityApiException(400, "VALIDATION_FAILED");
        }
        List<Long> activityIds = jdbcTemplate.queryForList(
                "SELECT activity_id FROM commerce_activity "
                        + "WHERE state = 'PUBLISHED' AND ends_at > UTC_TIMESTAMP(6) "
                        + "ORDER BY starts_at, activity_id LIMIT ?", Long.class, limit);
        if (activityIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(activityIds.size(), "?"));
        return queryActivities("WHERE a.activity_id IN (" + placeholders + ") "
                + "ORDER BY a.starts_at, a.activity_id, s.sku_id", activityIds.toArray())
                .stream().map(ActivityService::redactInventory).toList();
    }

    @Transactional(readOnly = true)
    public ActivityResponse publicActivity(long activityId) {
        if (activityId <= 0) {
            throw new ActivityApiException(400, "VALIDATION_FAILED");
        }
        List<ActivityResponse> matches = queryActivities(
                "WHERE a.activity_id = ? AND a.state = 'PUBLISHED' AND a.ends_at > UTC_TIMESTAMP(6) "
                        + "ORDER BY a.activity_id, s.sku_id", activityId);
        if (matches.isEmpty()) {
            throw new ActivityApiException(404, "ACTIVITY_NOT_FOUND");
        }
        return redactInventory(matches.getFirst());
    }

    @Transactional(readOnly = true)
    public ActivityResponse managementActivity(long activityId) {
        if (activityId <= 0) {
            throw new ActivityApiException(400, "VALIDATION_FAILED");
        }
        return activityById(activityId);
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> managementActivities(int limit) {
        if (limit < 1 || limit > 100) {
            throw new ActivityApiException(400, "VALIDATION_FAILED");
        }
        return queryActivities("WHERE a.activity_id IN (SELECT selected.activity_id FROM "
                + "(SELECT activity_id FROM commerce_activity ORDER BY updated_at DESC, activity_id DESC LIMIT ?) "
                + "selected) ORDER BY a.updated_at DESC, a.activity_id DESC, s.sku_id", limit);
    }

    private ActivityResponse activityById(long activityId) {
        List<ActivityResponse> matches = queryActivities(
                "WHERE a.activity_id = ? ORDER BY a.activity_id, s.sku_id", activityId);
        if (matches.isEmpty()) {
            throw new ActivityApiException(404, "ACTIVITY_NOT_FOUND");
        }
        return matches.getFirst();
    }

    private List<ActivityResponse> queryActivities(String conditionAndOrder, Object... args) {
        String sql = "SELECT a.activity_id, a.activity_code, a.title, a.state, a.starts_at, a.ends_at, "
                + "a.published_version, a.row_version, s.sku_id, c.sku_code, s.product_name_snapshot, "
                + "s.sku_name_snapshot, s.unit_price_minor, s.purchase_limit_per_user, "
                + "i.available_quantity, i.allocated_quantity FROM commerce_activity a "
                + "JOIN commerce_activity_sku s ON s.activity_id = a.activity_id "
                + "JOIN commerce_sku c ON c.sku_id = s.sku_id "
                + "JOIN commerce_inventory i ON i.activity_id = s.activity_id AND i.sku_id = s.sku_id "
                + conditionAndOrder;
        List<ActivityRowWithSku> rows = jdbcTemplate.query(sql,
                (rs, rowNum) -> new ActivityRowWithSku(
                        rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getObject(6, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getLong(7), rs.getLong(8),
                        new ActivitySkuResponse(rs.getLong(9), rs.getString(10), rs.getString(11),
                                rs.getString(12), rs.getLong(13), rs.getInt(14), rs.getLong(15) > 0,
                                rs.getLong(15), rs.getLong(16))),
                args);
        Map<Long, ActivityAccumulator> grouped = new LinkedHashMap<>();
        for (ActivityRowWithSku row : rows) {
            ActivityAccumulator activity = grouped.computeIfAbsent(row.activityId(), ignored ->
                    new ActivityAccumulator(row.activityId(), row.activityCode(), row.title(), row.state(),
                            row.startsAt(), row.endsAt(), row.publishedVersion(), row.rowVersion()));
            activity.skus().add(row.sku());
        }
        return grouped.values().stream().map(ActivityAccumulator::toResponse).toList();
    }

    private ActivityRow lockActivity(long activityId) {
        List<ActivityRow> rows = jdbcTemplate.query("SELECT activity_code, title, state, starts_at, ends_at, "
                        + "published_version, row_version FROM commerce_activity WHERE activity_id = ? FOR UPDATE",
                (rs, rowNum) -> new ActivityRow(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getObject(5, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        rs.getLong(6), rs.getLong(7)), activityId);
        if (rows.isEmpty()) {
            throw new ActivityApiException(404, "ACTIVITY_NOT_FOUND");
        }
        return rows.getFirst();
    }

    private long insertAndGetId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null || id.longValue() <= 0) {
            throw new IllegalStateException("Database did not return a generated commerce identifier");
        }
        return id.longValue();
    }

    private void audit(String actorId, String action, long activityId, Map<String, Object> changes) {
        jdbcTemplate.update("INSERT INTO commerce_audit_event "
                        + "(actor_id, action, target_type, target_id, request_id, result, changes) "
                        + "VALUES (?, ?, 'ACTIVITY', ?, ?, 'SUCCESS', ?)",
                actorId, action, Long.toString(activityId), UUID.randomUUID().toString(), toJson(changes));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize an activity audit snapshot", error);
        }
    }

    private static void validateActor(String actorId) {
        if (!StringUtils.hasText(actorId) || actorId.length() > 64) {
            throw new ActivityApiException(401, "UNAUTHENTICATED");
        }
    }

    private static void validateSku(CreateActivityRequest.ActivitySku sku) {
        if (sku == null || !StringUtils.hasText(sku.skuCode()) || sku.skuCode().length() > 64
                || !StringUtils.hasText(sku.productName()) || sku.productName().length() > 200
                || !StringUtils.hasText(sku.skuName()) || sku.skuName().length() > 200
                || sku.unitPriceMinor() == null || sku.unitPriceMinor() <= 0
                || sku.purchaseLimitPerUser() == null || sku.purchaseLimitPerUser() <= 0
                || sku.allocatedQuantity() == null || sku.allocatedQuantity() <= 0) {
            throw new ActivityApiException(400, "VALIDATION_FAILED");
        }
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static ActivityResponse redactInventory(ActivityResponse activity) {
        List<ActivitySkuResponse> publicSkus = activity.skus().stream()
                .map(sku -> new ActivitySkuResponse(sku.skuId(), sku.skuCode(), sku.productName(), sku.skuName(),
                        sku.unitPriceMinor(), sku.purchaseLimitPerUser(), sku.inStock(), null, null))
                .toList();
        return new ActivityResponse(activity.activityId(), activity.activityCode(), activity.title(), activity.state(),
                activity.startsAt(), activity.endsAt(), activity.publishedVersion(), activity.rowVersion(), publicSkus);
    }

    private record ActivityRow(String activityCode, String title, String state, Instant startsAt,
                               Instant endsAt, long publishedVersion, long rowVersion) {
    }

    private record ActivityRowWithSku(long activityId, String activityCode, String title, String state,
                                      Instant startsAt, Instant endsAt, long publishedVersion, long rowVersion,
                                      ActivitySkuResponse sku) {
    }

    private static final class ActivityAccumulator {
        private final long activityId;
        private final String activityCode;
        private final String title;
        private final String state;
        private final Instant startsAt;
        private final Instant endsAt;
        private final long publishedVersion;
        private final long rowVersion;
        private final List<ActivitySkuResponse> skus = new ArrayList<>();

        private ActivityAccumulator(long activityId, String activityCode, String title, String state,
                                    Instant startsAt, Instant endsAt, long publishedVersion, long rowVersion) {
            this.activityId = activityId;
            this.activityCode = activityCode;
            this.title = title;
            this.state = state;
            this.startsAt = startsAt;
            this.endsAt = endsAt;
            this.publishedVersion = publishedVersion;
            this.rowVersion = rowVersion;
        }

        private List<ActivitySkuResponse> skus() {
            return skus;
        }

        private ActivityResponse toResponse() {
            return new ActivityResponse(activityId, activityCode, title, state, startsAt, endsAt,
                    publishedVersion, rowVersion, List.copyOf(skus));
        }
    }
}

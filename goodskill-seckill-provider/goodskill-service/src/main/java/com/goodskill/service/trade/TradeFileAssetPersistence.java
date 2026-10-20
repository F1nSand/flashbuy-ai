package com.goodskill.service.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("production")
public class TradeFileAssetPersistence {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TradeFileAssetPersistence(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void beginUpload(TradeFileAsset asset, long ownerQuotaBytes) {
        jdbcTemplate.update("INSERT IGNORE INTO commerce_file_quota (owner_id, used_bytes) VALUES (?, 0)",
                asset.ownerId());
        Long usedBytes = jdbcTemplate.queryForObject("SELECT used_bytes FROM commerce_file_quota "
                + "WHERE owner_id = ? FOR UPDATE", Long.class, asset.ownerId());
        if (usedBytes == null || asset.sizeBytes() > ownerQuotaBytes - usedBytes) {
            throw new TradeFileApiException(413, "FILE_QUOTA_EXCEEDED");
        }
        jdbcTemplate.update("UPDATE commerce_file_quota SET used_bytes = used_bytes + ?, "
                        + "row_version = row_version + 1 WHERE owner_id = ?",
                asset.sizeBytes(), asset.ownerId());
        jdbcTemplate.update("INSERT INTO commerce_file_asset "
                        + "(file_id, owner_id, object_key, content_type, size_bytes, sha256, visibility, asset_state) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'UPLOADING')",
                asset.fileId(), asset.ownerId(), asset.objectKey(), asset.contentType(), asset.sizeBytes(),
                asset.sha256(), asset.visibility());
    }

    @Transactional
    public void activate(String fileId) {
        TradeFileAsset asset = jdbcTemplate.queryForObject("SELECT file_id, owner_id, object_key, content_type, "
                        + "size_bytes, sha256, visibility, asset_state FROM commerce_file_asset "
                        + "WHERE file_id = ? FOR UPDATE",
                (rs, rowNum) -> new TradeFileAsset(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8)), fileId);
        if (asset == null || !"UPLOADING".equals(asset.state())) {
            throw new IllegalStateException("File upload reservation is no longer active");
        }
        int updated = jdbcTemplate.update("UPDATE commerce_file_asset SET asset_state = 'ACTIVE' "
                        + "WHERE file_id = ? AND asset_state = 'UPLOADING'",
                fileId);
        if (updated != 1) {
            throw new IllegalStateException("File upload reservation is no longer active");
        }
        TradeAuditWriter.record(jdbcTemplate, objectMapper, asset.ownerId(), "FILE_UPLOAD", "FILE", fileId,
                "SUCCESS", null, java.util.Map.of("contentType", asset.contentType(),
                        "sizeBytes", asset.sizeBytes(), "visibility", asset.visibility()));
    }

    @Transactional
    public void failUpload(String fileId) {
        List<TradeFileAsset> rows = jdbcTemplate.query("SELECT file_id, owner_id, object_key, content_type, "
                        + "size_bytes, sha256, visibility, asset_state FROM commerce_file_asset "
                        + "WHERE file_id = ? FOR UPDATE",
                (rs, rowNum) -> new TradeFileAsset(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8)), fileId);
        if (rows.isEmpty() || !"UPLOADING".equals(rows.getFirst().state())) {
            return;
        }
        TradeFileAsset asset = rows.getFirst();
        int stateUpdated = jdbcTemplate.update("UPDATE commerce_file_asset SET asset_state = 'FAILED' "
                + "WHERE file_id = ? AND asset_state = 'UPLOADING'", fileId);
        if (stateUpdated != 1) {
            throw new IllegalStateException("File upload reservation changed while failure was recorded");
        }
        releaseQuota(asset.ownerId(), asset.sizeBytes());
    }

    @Transactional(readOnly = true)
    public TradeFileAsset findPublicAsset(String fileId) {
        List<TradeFileAsset> rows = jdbcTemplate.query("SELECT file_id, owner_id, object_key, content_type, "
                        + "size_bytes, sha256, visibility, asset_state FROM commerce_file_asset "
                        + "WHERE file_id = ? AND asset_state = 'ACTIVE' AND visibility = 'PRODUCT_PUBLIC'",
                (rs, rowNum) -> new TradeFileAsset(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8)), fileId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Transactional
    public boolean markDeletedByOwner(String fileId, String ownerId) {
        List<TradeFileAsset> rows = jdbcTemplate.query("SELECT file_id, owner_id, object_key, content_type, "
                        + "size_bytes, sha256, visibility, asset_state FROM commerce_file_asset "
                        + "WHERE file_id = ? FOR UPDATE",
                (rs, rowNum) -> new TradeFileAsset(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8)), fileId);
        if (rows.isEmpty() || !ownerId.equals(rows.getFirst().ownerId())
                || !"ACTIVE".equals(rows.getFirst().state())) {
            return false;
        }
        TradeFileAsset asset = rows.getFirst();
        int stateUpdated = jdbcTemplate.update("UPDATE commerce_file_asset SET asset_state = 'DELETED' "
                + "WHERE file_id = ? AND asset_state = 'ACTIVE'", fileId);
        if (stateUpdated != 1) {
            throw new IllegalStateException("File asset changed while deletion was recorded");
        }
        releaseQuota(ownerId, asset.sizeBytes());
        TradeAuditWriter.record(jdbcTemplate, objectMapper, ownerId, "FILE_DELETE", "FILE", fileId,
                "SUCCESS", null, java.util.Map.of("sizeBytes", asset.sizeBytes()));
        return true;
    }

    @Transactional(readOnly = true)
    public List<TradeFileAsset> findCleanupCandidates(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("File cleanup batch is outside the supported bound");
        }
        return jdbcTemplate.query("SELECT file_id, owner_id, object_key, content_type, size_bytes, sha256, "
                        + "visibility, asset_state FROM commerce_file_asset "
                        + "WHERE asset_state IN ('UPLOADING', 'FAILED', 'DELETED', 'CLEANING') "
                        + "AND updated_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 DAY) "
                        + "ORDER BY updated_at LIMIT ?",
                (rs, rowNum) -> new TradeFileAsset(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8)), limit);
    }

    @Transactional
    public boolean claimCleanupCandidate(String fileId) {
        List<TradeFileAsset> rows = jdbcTemplate.query("SELECT file_id, owner_id, size_bytes, asset_state "
                        + "FROM commerce_file_asset WHERE file_id = ? AND asset_state IN "
                        + "('UPLOADING', 'FAILED', 'DELETED', 'CLEANING') "
                        + "AND updated_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 DAY) FOR UPDATE",
                (rs, rowNum) -> new TradeFileAsset(rs.getString(1), rs.getString(2), null, null,
                        rs.getLong(3), null, null, rs.getString(4)), fileId);
        if (rows.isEmpty()) {
            return false;
        }
        TradeFileAsset asset = rows.getFirst();
        int claimed = jdbcTemplate.update("UPDATE commerce_file_asset SET asset_state = 'CLEANING', "
                        + "updated_at = UTC_TIMESTAMP(6) WHERE file_id = ? AND asset_state = ? "
                        + "AND updated_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 DAY)",
                fileId, asset.state());
        if (claimed != 1) {
            return false;
        }
        if ("UPLOADING".equals(asset.state())) {
            releaseQuota(asset.ownerId(), asset.sizeBytes());
        }
        return true;
    }

    @Transactional
    public void removeCleanupCandidate(String fileId) {
        jdbcTemplate.update("DELETE FROM commerce_file_asset WHERE file_id = ? AND asset_state = 'CLEANING'", fileId);
    }

    private void releaseQuota(String ownerId, long sizeBytes) {
        int updated = jdbcTemplate.update("UPDATE commerce_file_quota SET used_bytes = used_bytes - ?, "
                        + "row_version = row_version + 1 WHERE owner_id = ? AND used_bytes >= ?",
                sizeBytes, ownerId, sizeBytes);
        if (updated != 1) {
            throw new IllegalStateException("File quota ledger is inconsistent");
        }
    }
}

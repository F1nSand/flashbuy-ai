package com.goodskill.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Shared account-keyed throttle backed by the production identity database. */
@Service
@Profile("production")
public class DatabaseLoginAttemptLimiter implements LoginAttemptLimiter {

    private final JdbcTemplate jdbcTemplate;
    private final int maxAttempts;
    private final int windowSeconds;
    private final int lockSeconds;

    public DatabaseLoginAttemptLimiter(
            JdbcTemplate jdbcTemplate,
            @Value("${goodskill.auth.login.max-attempts:5}") int maxAttempts,
            @Value("${goodskill.auth.login.window-seconds:300}") int windowSeconds,
            @Value("${goodskill.auth.login.lock-seconds:900}") int lockSeconds) {
        if (maxAttempts < 1 || windowSeconds < 1 || lockSeconds < 1) {
            throw new IllegalArgumentException("Login throttle values must be positive");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
        this.lockSeconds = lockSeconds;
    }

    @Override
    public boolean isBlocked(String account) {
        String key = accountKey(account);
        List<Boolean> blocked = jdbcTemplate.query(
                "SELECT locked_until > UTC_TIMESTAMP(6) FROM auth_login_attempt "
                        + "WHERE account_key = ? AND locked_until IS NOT NULL",
                (rs, rowNum) -> rs.getBoolean(1), key);
        return !blocked.isEmpty() && Boolean.TRUE.equals(blocked.getFirst());
    }

    @Override
    @Transactional
    public void recordFailure(String account) {
        String key = accountKey(account);
        jdbcTemplate.update("INSERT IGNORE INTO auth_login_attempt "
                        + "(account_key, window_started_at, failed_attempts, locked_until) "
                        + "VALUES (?, UTC_TIMESTAMP(6), 0, NULL)", key);
        LoginWindow window = jdbcTemplate.queryForObject(
                "SELECT window_started_at, failed_attempts, UTC_TIMESTAMP(6) "
                        + "FROM auth_login_attempt WHERE account_key = ? FOR UPDATE",
                (rs, rowNum) -> new LoginWindow(rs.getTimestamp(1), rs.getInt(2), rs.getTimestamp(3)), key);

        Instant now = window.databaseNow().toInstant();
        boolean expired = !window.startedAt().toInstant().plusSeconds(windowSeconds).isAfter(now);
        int failures = expired ? 1
                : window.failedAttempts() >= Integer.MAX_VALUE ? Integer.MAX_VALUE : window.failedAttempts() + 1;
        Timestamp startedAt = expired ? window.databaseNow() : window.startedAt();
        jdbcTemplate.update("UPDATE auth_login_attempt SET window_started_at = ?, failed_attempts = ?, "
                        + "locked_until = CASE WHEN ? >= ? THEN DATE_ADD(UTC_TIMESTAMP(6), INTERVAL ? SECOND) "
                        + "ELSE NULL END WHERE account_key = ?",
                startedAt, failures, failures, maxAttempts, lockSeconds, key);
    }

    @Override
    @Transactional
    public void clear(String account) {
        jdbcTemplate.update("DELETE FROM auth_login_attempt WHERE account_key = ?", accountKey(account));
    }

    @Scheduled(fixedDelayString = "${goodskill.auth.login.cleanup-delay-ms:3600000}",
            initialDelayString = "${goodskill.auth.login.cleanup-delay-ms:3600000}")
    @Transactional
    public void removeExpiredEntries() {
        jdbcTemplate.update("DELETE FROM auth_login_attempt WHERE updated_at < "
                + "UTC_TIMESTAMP(6) - INTERVAL 1 DAY AND (locked_until IS NULL OR locked_until <= UTC_TIMESTAMP(6))");
    }

    private static String accountKey(String account) {
        String normalized = account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private record LoginWindow(Timestamp startedAt, int failedAttempts, Timestamp databaseNow) {
    }
}

package com.goodskill.service.trade;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Profile("production")
public class TradeCompensationTaskService {

    public static final String CLOSE_EXPIRED_ORDER = "CLOSE_EXPIRED_ORDER";
    public static final String PROCESS_REFUND = "PROCESS_REFUND";
    public static final int MAX_ATTEMPTS = 10;
    private static final int MAX_BATCH_SIZE = 100;
    private static final Pattern WORKER_ID = Pattern.compile("[A-Za-z0-9._:-]{1,80}");

    private final JdbcTemplate jdbcTemplate;
    private final TradeOrderLifecycleService lifecycleService;

    public TradeCompensationTaskService(JdbcTemplate jdbcTemplate,
                                        TradeOrderLifecycleService lifecycleService) {
        this.jdbcTemplate = jdbcTemplate;
        this.lifecycleService = lifecycleService;
    }

    @Transactional
    public int enqueueExpiredOrders(int limit) {
        validateBatchSize(limit);
        List<String> expiredOrderNumbers = lifecycleService.findExpiredOrderNumbers(limit);
        int inserted = 0;
        for (String orderNo : expiredOrderNumbers) {
            inserted += jdbcTemplate.update("INSERT IGNORE INTO commerce_compensation_task "
                            + "(task_key, task_type, aggregate_id, task_state, available_at) "
                            + "VALUES (?, ?, ?, 'PENDING', UTC_TIMESTAMP(6))",
                    "EXPIRE:" + orderNo, CLOSE_EXPIRED_ORDER, orderNo);
        }
        return inserted;
    }

    @Transactional
    public List<CompensationTask> claim(String workerId, int limit, int leaseSeconds) {
        validateWorkerAndLease(workerId, limit, leaseSeconds);
        jdbcTemplate.update("UPDATE commerce_compensation_task SET task_state = 'DEAD', "
                        + "last_error_code = 'LEASE_EXPIRED_AFTER_MAX_ATTEMPTS', lease_owner = NULL, lease_until = NULL "
                        + "WHERE task_state = 'PROCESSING' AND lease_until <= UTC_TIMESTAMP(6) AND attempt_count >= ?",
                MAX_ATTEMPTS);
        List<CompensationTask> tasks = jdbcTemplate.query("SELECT task_id, task_key, task_type, aggregate_id, "
                        + "attempt_count FROM commerce_compensation_task "
                        + "WHERE attempt_count < ? AND ((task_state = 'PENDING' AND available_at <= UTC_TIMESTAMP(6)) "
                        + "OR (task_state = 'PROCESSING' AND lease_until <= UTC_TIMESTAMP(6))) "
                        + "ORDER BY available_at, task_id LIMIT ? FOR UPDATE SKIP LOCKED",
                (rs, rowNum) -> new CompensationTask(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getInt(5) + 1), MAX_ATTEMPTS, limit);

        List<CompensationTask> claimed = new ArrayList<>(tasks.size());
        for (CompensationTask task : tasks) {
            int updated = jdbcTemplate.update("UPDATE commerce_compensation_task SET task_state = 'PROCESSING', "
                            + "attempt_count = attempt_count + 1, lease_owner = ?, "
                            + "lease_until = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL ? SECOND), last_error_code = NULL "
                            + "WHERE task_id = ? AND attempt_count < ?",
                    workerId, leaseSeconds, task.taskId(), MAX_ATTEMPTS);
            if (updated != 1) {
                throw new IllegalStateException("Compensation task changed while its row lock was held");
            }
            claimed.add(task);
        }
        return claimed;
    }

    @Transactional
    public void complete(CompensationTask task, String workerId) {
        int updated = jdbcTemplate.update("UPDATE commerce_compensation_task SET task_state = 'COMPLETED', "
                        + "completed_at = UTC_TIMESTAMP(6), lease_owner = NULL, lease_until = NULL "
                        + "WHERE task_id = ? AND task_state = 'PROCESSING' AND lease_owner = ?",
                task.taskId(), workerId);
        if (updated != 1) {
            throw new IllegalStateException("Compensation task lease was lost before completion");
        }
    }

    @Transactional
    public boolean fail(CompensationTask task, String workerId, String errorCode, int retryDelaySeconds) {
        if (!StringUtils.hasText(errorCode) || errorCode.length() > 80
                || retryDelaySeconds < 1 || retryDelaySeconds > 3600) {
            throw new IllegalArgumentException("Invalid compensation failure metadata");
        }
        int updated = jdbcTemplate.update("UPDATE commerce_compensation_task SET task_state = "
                        + "CASE WHEN attempt_count >= ? THEN 'DEAD' ELSE 'PENDING' END, "
                        + "available_at = CASE WHEN attempt_count >= ? THEN available_at "
                        + "ELSE DATE_ADD(UTC_TIMESTAMP(6), INTERVAL ? SECOND) END, "
                        + "last_error_code = ?, lease_owner = NULL, lease_until = NULL "
                        + "WHERE task_id = ? AND task_state = 'PROCESSING' AND lease_owner = ?",
                MAX_ATTEMPTS, MAX_ATTEMPTS, retryDelaySeconds, errorCode, task.taskId(), workerId);
        if (updated != 1) {
            throw new IllegalStateException("Compensation task lease was lost before failure was recorded");
        }
        return task.attemptCount() >= MAX_ATTEMPTS;
    }

    private static void validateWorkerAndLease(String workerId, int limit, int leaseSeconds) {
        if (workerId == null || !WORKER_ID.matcher(workerId).matches()
                || leaseSeconds < 5 || leaseSeconds > 3600) {
            throw new IllegalArgumentException("Invalid compensation worker or lease");
        }
        validateBatchSize(limit);
    }

    private static void validateBatchSize(int limit) {
        if (limit < 1 || limit > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Compensation batch size is outside the configured bound");
        }
    }

    public record CompensationTask(long taskId, String taskKey, String taskType, String aggregateId,
                                   int attemptCount) {
    }
}

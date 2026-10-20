package com.goodskill.service.trade;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TradeCompensationTaskServiceTest {

    @Test
    void refusesAnUnboundedClaimBeforeTouchingTheDatabase() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TradeOrderLifecycleService lifecycleService = mock(TradeOrderLifecycleService.class);
        TradeCompensationTaskService service = new TradeCompensationTaskService(jdbcTemplate, lifecycleService);

        assertThrows(IllegalArgumentException.class, () -> service.claim("worker-1", 101, 30));

        verifyNoInteractions(jdbcTemplate, lifecycleService);
    }

    @Test
    void sendsExhaustedTasksToDeadStateAndRetryableTasksBackToPending() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TradeOrderLifecycleService lifecycleService = mock(TradeOrderLifecycleService.class);
        TradeCompensationTaskService service = new TradeCompensationTaskService(jdbcTemplate, lifecycleService);
        when(jdbcTemplate.update(contains("SET task_state = "), any(Object[].class))).thenReturn(1);

        boolean dead = service.fail(task(10), "worker-1", "COMPENSATION_FAILED", 3600);
        boolean retryable = service.fail(task(9), "worker-1", "COMPENSATION_FAILED", 1800);

        assertTrue(dead);
        assertFalse(retryable);
    }

    private static TradeCompensationTaskService.CompensationTask task(int attemptCount) {
        return new TradeCompensationTaskService.CompensationTask(
                1, "EXPIRE:GS0123456789ABCDEF0123456789ABCDEF",
                TradeCompensationTaskService.CLOSE_EXPIRED_ORDER,
                "GS0123456789ABCDEF0123456789ABCDEF", attemptCount);
    }
}

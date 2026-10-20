package com.goodskill.service.trade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Profile("production")
public class TradeOrderExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(TradeOrderExpiryScheduler.class);
    private static final int BATCH_SIZE = 100;
    private static final int LEASE_SECONDS = 30;
    private static final String WORKER_ID = "expiry:" + UUID.randomUUID();

    private final TradePaymentService paymentService;
    private final TradeCompensationTaskService taskService;

    public TradeOrderExpiryScheduler(TradePaymentService paymentService,
                                     TradeCompensationTaskService taskService) {
        this.paymentService = paymentService;
        this.taskService = taskService;
    }

    @Scheduled(fixedDelayString = "${goodskill.trade.expiry-scan-delay-ms:5000}",
            initialDelayString = "${goodskill.trade.expiry-scan-initial-delay-ms:5000}")
    public void closeExpiredOrders() {
        try {
            taskService.enqueueExpiredOrders(BATCH_SIZE);
            List<TradeCompensationTaskService.CompensationTask> tasks =
                    taskService.claim(WORKER_ID, BATCH_SIZE, LEASE_SECONDS);
            for (TradeCompensationTaskService.CompensationTask task : tasks) {
                process(task);
            }
        } catch (RuntimeException error) {
            log.error("Could not run expired-order compensation; failureType={}", error.getClass().getSimpleName());
        }

    }

    private void process(TradeCompensationTaskService.CompensationTask task) {
        try {
            if (TradeCompensationTaskService.CLOSE_EXPIRED_ORDER.equals(task.taskType())) {
                paymentService.processExpiredOrder(task.aggregateId());
            } else if (TradeCompensationTaskService.PROCESS_REFUND.equals(task.taskType())) {
                paymentService.processRefund(task.aggregateId());
            } else {
                throw new IllegalStateException("Unsupported compensation task type");
            }
            taskService.complete(task, WORKER_ID);
        } catch (RuntimeException error) {
            int retryDelay = Math.min(3600, 5 << Math.min(task.attemptCount() - 1, 9));
            try {
                boolean dead = taskService.fail(task, WORKER_ID, "COMPENSATION_FAILED", retryDelay);
                if (dead) {
                    log.error("Commerce compensation task exhausted retries; taskKey={}, failureType={}",
                            task.taskKey(), error.getClass().getSimpleName());
                } else {
                    log.warn("Commerce compensation task will retry; taskKey={}, attempt={}, failureType={}",
                            task.taskKey(), task.attemptCount(), error.getClass().getSimpleName());
                }
            } catch (RuntimeException persistenceError) {
                log.error("Could not record compensation task failure; taskKey={}, failureType={}",
                        task.taskKey(), persistenceError.getClass().getSimpleName());
            }
        }
    }
}

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
public class TradeOutboxPublisherScheduler {

    private static final Logger log = LoggerFactory.getLogger(TradeOutboxPublisherScheduler.class);
    private static final int BATCH_SIZE = 10;
    private static final int LEASE_SECONDS = 120;
    private static final String WORKER_ID = "outbox:" + UUID.randomUUID();

    private final TradeOutboxStore outboxStore;
    private final TradeOutboxRabbitPublisher publisher;

    public TradeOutboxPublisherScheduler(TradeOutboxStore outboxStore, TradeOutboxRabbitPublisher publisher) {
        this.outboxStore = outboxStore;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${goodskill.trade.outbox-publish-delay-ms:1000}",
            initialDelayString = "${goodskill.trade.outbox-publish-initial-delay-ms:10000}")
    public void publishPendingEvents() {
        try {
            List<TradeOutboxEvent> events = outboxStore.claim(WORKER_ID, BATCH_SIZE, LEASE_SECONDS);
            for (TradeOutboxEvent event : events) {
                publish(event);
            }
        } catch (RuntimeException error) {
            log.error("Could not claim commerce outbox events; failureType={}", error.getClass().getSimpleName());
        }
    }

    private void publish(TradeOutboxEvent event) {
        try {
            publisher.publish(event);
            outboxStore.markPublished(event.eventId(), WORKER_ID);
        } catch (RuntimeException error) {
            int retryDelay = Math.min(3600, 5 << Math.min(event.attemptCount() - 1, 9));
            try {
                boolean dead = outboxStore.markFailed(event, WORKER_ID, "PUBLISH_FAILED", retryDelay);
                if (dead) {
                    log.error("Commerce outbox event exhausted retries; eventId={}, failureType={}",
                            event.eventId(), error.getClass().getSimpleName());
                } else {
                    log.warn("Commerce outbox event will retry; eventId={}, attempt={}, failureType={}",
                            event.eventId(), event.attemptCount(), error.getClass().getSimpleName());
                }
            } catch (RuntimeException persistenceError) {
                log.error("Could not persist commerce outbox failure; eventId={}, failureType={}",
                        event.eventId(), persistenceError.getClass().getSimpleName());
            }
        }
    }
}

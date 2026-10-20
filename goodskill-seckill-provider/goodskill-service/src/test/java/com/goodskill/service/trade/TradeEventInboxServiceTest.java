package com.goodskill.service.trade;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TradeEventInboxServiceTest {

    @Test
    void persistsAnEventOnceAndTreatsDuplicateDeliveryAsAlreadyReceived() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(contains("INSERT IGNORE INTO commerce_event_inbox"), any(Object[].class)))
                .thenReturn(1, 0);
        TradeEventInboxService service = new TradeEventInboxService(jdbcTemplate);

        assertTrue(service.persist(event()));
        assertFalse(service.persist(event()));
    }

    @Test
    void rejectsMalformedEventBeforeDatabaseWrite() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TradeEventInboxService service = new TradeEventInboxService(jdbcTemplate);
        TradeEventEnvelope malformed = new TradeEventEnvelope(
                "not-an-event-id", "ORDER", "order-1", "OrderCreated", 1, "{}");

        assertThrows(IllegalArgumentException.class, () -> service.persist(malformed));

        verifyNoInteractions(jdbcTemplate);
    }

    private static TradeEventEnvelope event() {
        return new TradeEventEnvelope("01234567-89ab-cdef-0123-456789abcdef", "ORDER",
                "GS0123456789ABCDEF0123456789ABCDEF", "OrderCreated", 1, "{\"orderNo\":\"GS0123456789ABCDEF0123456789ABCDEF\"}");
    }
}

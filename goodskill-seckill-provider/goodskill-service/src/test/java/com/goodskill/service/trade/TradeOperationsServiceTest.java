package com.goodskill.service.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TradeOperationsServiceTest {

    @Test
    void rejectsUnknownOrderFilterBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TradeOperationsService service = new TradeOperationsService(jdbc, new ObjectMapper());

        TradeOrderApiException error = assertThrows(TradeOrderApiException.class,
                () -> service.listOrders("NOT_A_STATE", 1, 20));

        assertEquals("VALIDATION_FAILED", error.errorCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsUnboundedOrderPageBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TradeOperationsService service = new TradeOperationsService(jdbc, new ObjectMapper());

        TradeOrderApiException error = assertThrows(TradeOrderApiException.class,
                () -> service.listOrders(null, 1, 101));

        assertEquals("VALIDATION_FAILED", error.errorCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsInvalidOrderNumberBeforeLoadingShippingSnapshot() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TradeOperationsService service = new TradeOperationsService(jdbc, new ObjectMapper());

        TradeOrderApiException error = assertThrows(TradeOrderApiException.class,
                () -> service.orderDetail("not-an-order"));

        assertEquals("VALIDATION_FAILED", error.errorCode());
        verifyNoInteractions(jdbc);
    }
}

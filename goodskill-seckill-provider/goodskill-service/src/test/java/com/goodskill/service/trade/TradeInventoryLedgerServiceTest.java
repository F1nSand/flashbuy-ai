package com.goodskill.service.trade;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TradeInventoryLedgerServiceTest {

    @Test
    void requiresAnIndexedFilterAndDoesNotScanTheWholeLedger() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TradeInventoryLedgerService service = new TradeInventoryLedgerService(jdbc);

        TradeOrderApiException error = assertThrows(TradeOrderApiException.class,
                () -> service.list(null, null, null, 1, 50));

        assertEquals("VALIDATION_FAILED", error.errorCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsMalformedOrderFilterBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TradeInventoryLedgerService service = new TradeInventoryLedgerService(jdbc);

        TradeOrderApiException error = assertThrows(TradeOrderApiException.class,
                () -> service.list(null, null, "OTHER-ORDER", 1, 50));

        assertEquals("VALIDATION_FAILED", error.errorCode());
        verifyNoInteractions(jdbc);
    }
}

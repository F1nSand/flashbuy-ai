package com.goodskill.service.trade;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TradeAuditServiceTest {

    @Test
    void rejectsInjectionLikeFiltersAndOversizedPagesBeforeQueryingDatabase() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TradeAuditService service = new TradeAuditService(jdbcTemplate);

        TradeOrderApiException badFilter = assertThrows(TradeOrderApiException.class,
                () -> service.list("manager-1", "ORDER; DROP TABLE commerce_order", null,
                        null, 1, 50));
        TradeOrderApiException badPage = assertThrows(TradeOrderApiException.class,
                () -> service.list(null, null, null, null, 1, 101));

        assertEquals("VALIDATION_FAILED", badFilter.errorCode());
        assertEquals("VALIDATION_FAILED", badPage.errorCode());
        verifyNoInteractions(jdbcTemplate);
    }
}

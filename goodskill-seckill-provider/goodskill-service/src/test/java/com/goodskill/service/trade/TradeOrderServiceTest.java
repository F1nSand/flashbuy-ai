package com.goodskill.service.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TradeOrderServiceTest {

    @Test
    void closedAdmissionRejectsBeforeCreatingAnIdempotencyRecord() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TradeOrderService service = new TradeOrderService(jdbcTemplate, new ObjectMapper(), 900, false);
        CreateTradeOrderRequest request = new CreateTradeOrderRequest(7L, 1,
                new CreateTradeOrderRequest.ShippingAddress(
                        "Buyer", "+8613800000000", "Zhejiang", "Hangzhou", "Xihu", "Example Road 1"));

        TradeOrderOutcome outcome = service.create(42L, "buyer-1", "order-create-request-0001", request);

        assertEquals("ADMISSION_PAUSED", outcome.errorCode());
        assertEquals(503, outcome.status());
        verifyNoInteractions(jdbcTemplate);
    }
}

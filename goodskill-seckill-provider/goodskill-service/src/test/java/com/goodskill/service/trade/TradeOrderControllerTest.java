package com.goodskill.service.trade;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TradeOrderControllerTest {

    private static final String IDEMPOTENCY_KEY = "order-create-request-0001";
    private static final CreateTradeOrderRequest REQUEST = new CreateTradeOrderRequest(
            7L, 1, new CreateTradeOrderRequest.ShippingAddress(
            "Buyer", "+8613800000000", "Zhejiang", "Hangzhou", "Xihu", "Example Road 1"));

    @Test
    void createsOrderAndReturnsCreated() {
        TradeOrderService service = mock(TradeOrderService.class);
        TradeOrderResponse order = new TradeOrderResponse(
                "GS0123456789ABCDEF", "PENDING_PAYMENT", Instant.parse("2026-09-28T10:00:00Z"),
                1099, "CNY", false);
        when(service.create(42L, "buyer-1", IDEMPOTENCY_KEY, REQUEST))
                .thenReturn(TradeOrderOutcome.success(order));

        var response = new TradeOrderController(service)
                .create(42L, "buyer-1", IDEMPOTENCY_KEY, REQUEST);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(0, response.getBody().getCode());
        assertEquals(order, response.getBody().getData());
    }

    @Test
    void returnsUnauthorizedWithoutCallingTradeServiceWhenIdentityIsMissing() {
        TradeOrderService service = mock(TradeOrderService.class);

        var response = new TradeOrderController(service)
                .create(42L, null, IDEMPOTENCY_KEY, REQUEST);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("UNAUTHENTICATED", response.getBody().getMsg());
        verifyNoInteractions(service);
    }

    @Test
    void mapsDatabaseUnavailableToStableServiceError() {
        TradeOrderService service = mock(TradeOrderService.class);
        when(service.create(42L, "buyer-1", IDEMPOTENCY_KEY, REQUEST))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        var response = new TradeOrderController(service)
                .create(42L, "buyer-1", IDEMPOTENCY_KEY, REQUEST);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("TEMPORARY_UNAVAILABLE", response.getBody().getMsg());
        verify(service).create(42L, "buyer-1", IDEMPOTENCY_KEY, REQUEST);
    }

    @Test
    void replaysOrderWithOkStatus() {
        TradeOrderService service = mock(TradeOrderService.class);
        TradeOrderResponse order = new TradeOrderResponse(
                "GS0123456789ABCDEF", "PENDING_PAYMENT", Instant.parse("2026-09-28T10:00:00Z"),
                1099, "CNY", true);
        when(service.create(42L, "buyer-1", IDEMPOTENCY_KEY, REQUEST))
                .thenReturn(TradeOrderOutcome.success(order));

        var response = new TradeOrderController(service)
                .create(42L, "buyer-1", IDEMPOTENCY_KEY, REQUEST);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().replayed());
    }
}

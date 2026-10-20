package com.goodskill.service.trade;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TradeOrderLifecycleControllerTest {

    private static final String ORDER_NO = "GS0123456789ABCDEF0123456789ABCDEF";
    private static final String IDEMPOTENCY_KEY = "order-cancel-request-0001";
    private static final TradeOrderResponse ORDER = new TradeOrderResponse(
            ORDER_NO, "PENDING_PAYMENT", Instant.parse("2026-09-28T10:00:00Z"), 1099, "CNY", false);

    @Test
    void listsOnlyServiceReturnedBuyerOrders() {
        TradeOrderLifecycleService service = mock(TradeOrderLifecycleService.class);
        TradeOrderPageResponse page = new TradeOrderPageResponse(1, 20, 1, List.of(ORDER));
        when(service.list("buyer-1", 1, 20)).thenReturn(page);

        var response = new TradeOrderLifecycleController(service).list("buyer-1", 1, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(page, response.getBody().getData());
        verify(service).list("buyer-1", 1, 20);
    }

    @Test
    void rejectsMissingBuyerIdentityBeforeServiceCall() {
        TradeOrderLifecycleService service = mock(TradeOrderLifecycleService.class);

        var response = new TradeOrderLifecycleController(service).detail(null, ORDER_NO);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("UNAUTHENTICATED", response.getBody().getMsg());
        verifyNoInteractions(service);
    }

    @Test
    void hidesOrdersNotOwnedByTheBuyerAsNotFound() {
        TradeOrderLifecycleService service = mock(TradeOrderLifecycleService.class);
        when(service.detail("buyer-1", ORDER_NO)).thenThrow(new TradeOrderApiException(404, "ORDER_NOT_FOUND"));

        var response = new TradeOrderLifecycleController(service).detail("buyer-1", ORDER_NO);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("ORDER_NOT_FOUND", response.getBody().getMsg());
    }

    @Test
    void cancelsWithAnIdempotencyKeyAndReturnsClosedOrder() {
        TradeOrderLifecycleService service = mock(TradeOrderLifecycleService.class);
        TradeOrderResponse closed = new TradeOrderResponse(
                ORDER_NO, "CLOSED", ORDER.paymentDeadline(), 1099, "CNY", false);
        when(service.cancel("buyer-1", ORDER_NO, IDEMPOTENCY_KEY))
                .thenReturn(TradeOrderOutcome.success(closed));

        var response = new TradeOrderLifecycleController(service)
                .cancel("buyer-1", IDEMPOTENCY_KEY, ORDER_NO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(closed, response.getBody().getData());
        verify(service).cancel("buyer-1", ORDER_NO, IDEMPOTENCY_KEY);
    }

    @Test
    void mapsDatabaseFailureToTemporaryUnavailable() {
        TradeOrderLifecycleService service = mock(TradeOrderLifecycleService.class);
        when(service.list("buyer-1", 1, 20))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        var response = new TradeOrderLifecycleController(service).list("buyer-1", 1, 20);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("TEMPORARY_UNAVAILABLE", response.getBody().getMsg());
    }
}

package com.goodskill.service.trade;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradePaymentServiceTest {

    private static final String ORDER_NO = "GS0123456789ABCDEF0123456789ABCDEF";
    private static final String PAYMENT_NO = "GP0123456789ABCDEF0123456789ABCDEF";

    @Test
    void disabledPaymentDoesNotCreateAnAttempt() {
        TradePaymentProvider provider = new DisabledTradePaymentProvider();
        TradeOrderLifecycleService lifecycle = mock(TradeOrderLifecycleService.class);

        TradeOrderApiException error = assertThrows(TradeOrderApiException.class,
                () -> new TradePaymentService(provider, lifecycle)
                        .createCheckout("buyer-1", ORDER_NO, "page"));

        assertEquals("PAYMENT_DISABLED", error.errorCode());
        verify(lifecycle, never()).preparePayment("buyer-1", ORDER_NO, "page");
    }

    @Test
    void disabledPaymentDoesNotPersistARefundRequest() {
        TradeOrderLifecycleService lifecycle = mock(TradeOrderLifecycleService.class);

        TradeOrderApiException error = assertThrows(TradeOrderApiException.class,
                () -> new TradePaymentService(new DisabledTradePaymentProvider(), lifecycle)
                        .requestFullRefund("manager-1", ORDER_NO, "refund-request-0001", "requested by buyer"));

        assertEquals("PAYMENT_DISABLED", error.errorCode());
        verifyNoInteractions(lifecycle);
    }

    @Test
    void checkoutUsesPersistedPaymentAttemptAndReturnsProviderForm() {
        TradePaymentProvider provider = mock(TradePaymentProvider.class);
        TradeOrderLifecycleService lifecycle = mock(TradeOrderLifecycleService.class);
        when(provider.enabled()).thenReturn(true);
        when(lifecycle.preparePayment("buyer-1", ORDER_NO, "page"))
                .thenReturn(new TradeOrderLifecycleService.PaymentCheckoutDetails(
                        PAYMENT_NO, "Flash item", 1099, 900, "page"));
        when(provider.createCheckout(new TradePaymentProvider.CheckoutRequest(
                PAYMENT_NO, "Flash item", 1099, 900, "page"))).thenReturn("<form></form>");

        TradePaymentCheckoutResponse result = new TradePaymentService(provider, lifecycle)
                .createCheckout("buyer-1", ORDER_NO, "page");

        assertEquals(PAYMENT_NO, result.paymentNo());
        assertEquals(1099, result.amountMinor());
        assertEquals("<form></form>", result.formHtml());
        verify(lifecycle).markPaymentPending(PAYMENT_NO);
    }

    @Test
    void onlyVerifiedSuccessfulNotificationsEnterPaymentSettlement() {
        FakePaymentProvider provider = new FakePaymentProvider();
        TradeOrderLifecycleService lifecycle = mock(TradeOrderLifecycleService.class);
        provider.notification = new TradePaymentProvider.VerifiedNotification("app", "seller", PAYMENT_NO,
                "trade-1", 1099, "TRADE_SUCCESS");

        new TradePaymentService(provider, lifecycle).handleNotification(Map.of("sign", "signed"));

        verify(lifecycle).applyConfirmedPayment(new TradePaymentProvider.ConfirmedPayment(PAYMENT_NO, "trade-1", 1099));
        verify(lifecycle, never()).recordNonPaidNotification(
                new TradePaymentProvider.VerifiedNotification("app", "seller", PAYMENT_NO,
                        "trade-1", 1099, "TRADE_SUCCESS"));
    }

    @Test
    void invalidNotificationNeverReachesTheOrderStateService() {
        FakePaymentProvider provider = new FakePaymentProvider();
        provider.notificationFailure = new PaymentChannelException("invalid signature");
        TradeOrderLifecycleService lifecycle = mock(TradeOrderLifecycleService.class);

        assertThrows(PaymentChannelException.class,
                () -> new TradePaymentService(provider, lifecycle).handleNotification(Map.of("sign", "bad")));

        verifyNoInteractions(lifecycle);
    }

    @Test
    void expiredOrderIsReleasedOnlyAfterProviderConfirmsClose() {
        FakePaymentProvider provider = new FakePaymentProvider();
        provider.paymentLookup = new TradePaymentProvider.PaymentLookup(
                TradePaymentProvider.PaymentStatus.WAITING, null, 0);
        provider.closeLookup = new TradePaymentProvider.PaymentLookup(
                TradePaymentProvider.PaymentStatus.CLOSED, null, 0);
        TradeOrderLifecycleService lifecycle = mock(TradeOrderLifecycleService.class);
        when(lifecycle.prepareExpiryCheck(ORDER_NO))
                .thenReturn(new TradeOrderLifecycleService.PaymentCheck(ORDER_NO, PAYMENT_NO, 1099));

        new TradePaymentService(provider, lifecycle).processExpiredOrder(ORDER_NO);

        assertEquals(1, provider.closeCalls);
        verify(lifecycle).closeExpiredAfterProviderClose(ORDER_NO, PAYMENT_NO);
    }

    @Test
    void unknownProviderStatusDoesNotReleaseAnExpiredReservation() {
        FakePaymentProvider provider = new FakePaymentProvider();
        provider.paymentLookup = new TradePaymentProvider.PaymentLookup(
                TradePaymentProvider.PaymentStatus.UNKNOWN, null, 0);
        TradeOrderLifecycleService lifecycle = mock(TradeOrderLifecycleService.class);
        when(lifecycle.prepareExpiryCheck(ORDER_NO))
                .thenReturn(new TradeOrderLifecycleService.PaymentCheck(ORDER_NO, PAYMENT_NO, 1099));

        assertThrows(PaymentChannelException.class,
                () -> new TradePaymentService(provider, lifecycle).processExpiredOrder(ORDER_NO));

        assertEquals(0, provider.closeCalls);
        verify(lifecycle, never()).closeExpiredAfterProviderClose(ORDER_NO, PAYMENT_NO);
    }

    @Test
    void fullRefundCannotCompleteWhenProviderReportsAPartialAmount() {
        FakePaymentProvider provider = new FakePaymentProvider();
        provider.refundLookup = new TradePaymentProvider.RefundLookup(
                TradePaymentProvider.RefundStatus.SUCCEEDED, 500);
        TradeOrderLifecycleService lifecycle = mock(TradeOrderLifecycleService.class);
        when(lifecycle.loadRefund(ORDER_NO)).thenReturn(new TradeOrderLifecycleService.RefundPlan(
                "RF0123456789ABCDEF0123456789ABCDEF", ORDER_NO, PAYMENT_NO, 1099,
                "test reason", "PENDING", "REFUNDING"));

        assertThrows(PaymentChannelException.class,
                () -> new TradePaymentService(provider, lifecycle).processRefund(ORDER_NO));

        verify(lifecycle, never()).recordRefundOutcome("RF0123456789ABCDEF0123456789ABCDEF",
                TradePaymentProvider.RefundStatus.SUCCEEDED);
    }

    @Test
    void disabledChannelStillAllowsExpiryReleaseForOrdersWithNoPaymentAttempt() {
        TradePaymentProvider provider = new DisabledTradePaymentProvider();
        TradeOrderLifecycleService lifecycle = mock(TradeOrderLifecycleService.class);
        when(lifecycle.prepareExpiryCheck(ORDER_NO))
                .thenReturn(new TradeOrderLifecycleService.PaymentCheck(ORDER_NO, null, 1099));

        new TradePaymentService(provider, lifecycle).processExpiredOrder(ORDER_NO);

        verify(lifecycle).closeExpired(ORDER_NO);
    }

    private static final class FakePaymentProvider implements TradePaymentProvider {
        private VerifiedNotification notification;
        private RuntimeException notificationFailure;
        private PaymentLookup paymentLookup = new PaymentLookup(PaymentStatus.UNKNOWN, null, 0);
        private PaymentLookup closeLookup = new PaymentLookup(PaymentStatus.UNKNOWN, null, 0);
        private RefundLookup refundLookup = new RefundLookup(RefundStatus.UNKNOWN, 0);
        private int closeCalls;

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public String createCheckout(CheckoutRequest request) {
            return "<form></form>";
        }

        @Override
        public VerifiedNotification verifyNotification(Map<String, String> parameters) {
            if (notificationFailure != null) {
                throw notificationFailure;
            }
            return notification;
        }

        @Override
        public PaymentLookup query(String paymentNo) {
            return paymentLookup;
        }

        @Override
        public PaymentLookup close(String paymentNo) {
            closeCalls++;
            return closeLookup;
        }

        @Override
        public RefundStatus refund(RefundRequest request) {
            return RefundStatus.PENDING;
        }

        @Override
        public RefundLookup queryRefund(String paymentNo, String refundNo) {
            return refundLookup;
        }
    }
}

package com.goodskill.service.trade;

import java.util.Map;

/** External payment channel boundary. Business state remains owned by the trade database. */
public interface TradePaymentProvider {

    boolean enabled();

    String createCheckout(CheckoutRequest request);

    VerifiedNotification verifyNotification(Map<String, String> parameters);

    PaymentLookup query(String paymentNo);

    PaymentLookup close(String paymentNo);

    RefundStatus refund(RefundRequest request);

    RefundLookup queryRefund(String paymentNo, String refundNo);

    record CheckoutRequest(String paymentNo, String subject, long amountMinor, long remainingSeconds, String mode) {
    }

    record VerifiedNotification(String appId, String sellerId, String paymentNo, String providerTradeNo,
                                long amountMinor, String tradeStatus) {
    }

    record PaymentLookup(PaymentStatus status, String providerTradeNo, long amountMinor) {
    }

    record ConfirmedPayment(String paymentNo, String providerTradeNo, long amountMinor) {
    }

    record RefundLookup(RefundStatus status, long amountMinor) {
    }

    record RefundRequest(String paymentNo, String refundNo, long amountMinor, String reason) {
    }

    enum PaymentStatus {
        NOT_FOUND,
        WAITING,
        PAID,
        CLOSED,
        UNKNOWN
    }

    enum RefundStatus {
        SUCCEEDED,
        PENDING,
        FAILED,
        UNKNOWN
    }
}

package com.goodskill.service.trade;

import java.util.Map;

final class DisabledTradePaymentProvider implements TradePaymentProvider {

    @Override
    public boolean enabled() {
        return false;
    }

    private PaymentChannelException disabled() {
        return new PaymentChannelException("Payment channel is disabled");
    }

    @Override
    public String createCheckout(CheckoutRequest request) {
        throw disabled();
    }

    @Override
    public VerifiedNotification verifyNotification(Map<String, String> parameters) {
        throw disabled();
    }

    @Override
    public PaymentLookup query(String paymentNo) {
        throw disabled();
    }

    @Override
    public PaymentLookup close(String paymentNo) {
        throw disabled();
    }

    @Override
    public RefundStatus refund(RefundRequest request) {
        throw disabled();
    }

    @Override
    public RefundLookup queryRefund(String paymentNo, String refundNo) {
        throw disabled();
    }
}

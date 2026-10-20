package com.goodskill.service.trade;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.regex.Pattern;

@Service
@Profile("production")
public class TradePaymentService {

    private static final Pattern ORDER_NO = Pattern.compile("GS[0-9A-F]{32}");

    private final TradePaymentProvider provider;
    private final TradeOrderLifecycleService lifecycleService;

    public TradePaymentService(TradePaymentProvider provider, TradeOrderLifecycleService lifecycleService) {
        this.provider = provider;
        this.lifecycleService = lifecycleService;
    }

    public TradePaymentCheckoutResponse createCheckout(String buyerId, String orderNo, String mode) {
        if (!provider.enabled()) {
            throw new TradeOrderApiException(503, "PAYMENT_DISABLED");
        }
        TradeOrderLifecycleService.PaymentCheckoutDetails details =
                lifecycleService.preparePayment(buyerId, orderNo, mode);
        String form = provider.createCheckout(new TradePaymentProvider.CheckoutRequest(
                details.paymentNo(), details.subject(), details.amountMinor(), details.remainingSeconds(),
                details.mode()));
        lifecycleService.markPaymentPending(details.paymentNo());
        return new TradePaymentCheckoutResponse(details.paymentNo(), details.amountMinor(), "CNY", form);
    }

    public TradeOrderLifecycleService.RefundPlan requestFullRefund(String actorId, String orderNo,
                                                                    String idempotencyKey, String reason) {
        if (!provider.enabled()) {
            throw new TradeOrderApiException(503, "PAYMENT_DISABLED");
        }
        return lifecycleService.requestFullRefund(actorId, orderNo, idempotencyKey, reason);
    }

    public void handleNotification(Map<String, String> parameters) {
        if (!provider.enabled()) {
            throw new TradeOrderApiException(503, "PAYMENT_DISABLED");
        }
        TradePaymentProvider.VerifiedNotification notification = provider.verifyNotification(parameters);
        if ("TRADE_SUCCESS".equals(notification.tradeStatus())
                || "TRADE_FINISHED".equals(notification.tradeStatus())) {
            lifecycleService.applyConfirmedPayment(new TradePaymentProvider.ConfirmedPayment(
                    notification.paymentNo(), notification.providerTradeNo(), notification.amountMinor()));
        } else {
            lifecycleService.recordNonPaidNotification(notification);
        }
    }

    public void processExpiredOrder(String orderNo) {
        TradeOrderLifecycleService.PaymentCheck check = lifecycleService.prepareExpiryCheck(orderNo);
        if (check == null) {
            return;
        }
        if (check.paymentNo() == null) {
            lifecycleService.closeExpired(orderNo);
            return;
        }
        if (!provider.enabled()) {
            throw new PaymentChannelException("Payment must be enabled before a payment attempt can be closed");
        }
        TradePaymentProvider.PaymentLookup result = provider.query(check.paymentNo());
        if (result.status() == TradePaymentProvider.PaymentStatus.PAID) {
            settleLookup(check, result);
            return;
        }
        if (result.status() == TradePaymentProvider.PaymentStatus.UNKNOWN) {
            throw new PaymentChannelException("Alipay payment state is unknown");
        }
        if (result.status() == TradePaymentProvider.PaymentStatus.WAITING
                || result.status() == TradePaymentProvider.PaymentStatus.NOT_FOUND) {
            result = provider.close(check.paymentNo());
        }
        if (result.status() == TradePaymentProvider.PaymentStatus.PAID) {
            settleLookup(check, result);
        } else if (result.status() == TradePaymentProvider.PaymentStatus.CLOSED) {
            lifecycleService.closeExpiredAfterProviderClose(orderNo, check.paymentNo());
        } else {
            throw new PaymentChannelException("Alipay has not confirmed that the payment is closed");
        }
    }

    public void processRefund(String orderNo) {
        if (!provider.enabled()) {
            throw new PaymentChannelException("Payment must be enabled before a refund can be processed");
        }
        TradeOrderLifecycleService.RefundPlan refund = lifecycleService.loadRefund(orderNo);
        if (refund == null) {
            throw new TradeOrderApiException(404, "REFUND_NOT_FOUND");
        }
        if ("SUCCEEDED".equals(refund.state()) || "FAILED".equals(refund.state())) {
            return;
        }
        if (!StringUtils.hasText(refund.paymentNo())) {
            throw new IllegalStateException("Refund does not reference a successful payment attempt");
        }

        TradePaymentProvider.RefundLookup lookup = provider.queryRefund(refund.paymentNo(), refund.refundNo());
        TradePaymentProvider.RefundStatus result = lookup.status();
        if (result == TradePaymentProvider.RefundStatus.UNKNOWN) {
            throw new PaymentChannelException("Alipay refund state is unknown");
        }
        if (result == TradePaymentProvider.RefundStatus.PENDING) {
            TradePaymentProvider.RefundStatus submitted = provider.refund(
                    new TradePaymentProvider.RefundRequest(refund.paymentNo(), refund.refundNo(),
                            refund.amountMinor(), refund.reason()));
            if (submitted == TradePaymentProvider.RefundStatus.UNKNOWN) {
                throw new PaymentChannelException("Alipay did not confirm the refund request");
            }
            lookup = provider.queryRefund(refund.paymentNo(), refund.refundNo());
            result = lookup.status();
        }
        if (result == TradePaymentProvider.RefundStatus.PENDING) {
            if (lookup.amountMinor() != 0 && lookup.amountMinor() != refund.amountMinor()) {
                throw new PaymentChannelException("Alipay refund amount does not match the full order amount");
            }
            lifecycleService.recordRefundOutcome(refund.refundNo(), result);
            throw new PaymentChannelException("Alipay refund is still processing");
        }
        if (result == TradePaymentProvider.RefundStatus.SUCCEEDED
                && lookup.amountMinor() != refund.amountMinor()) {
            throw new PaymentChannelException("Alipay refund amount does not match the full order amount");
        }
        lifecycleService.recordRefundOutcome(refund.refundNo(), result);
    }

    private void settleLookup(TradeOrderLifecycleService.PaymentCheck check,
                              TradePaymentProvider.PaymentLookup result) {
        if (!StringUtils.hasText(result.providerTradeNo()) || result.amountMinor() != check.amountMinor()) {
            throw new PaymentChannelException("Alipay query result does not match the pending order");
        }
        lifecycleService.applyConfirmedPayment(new TradePaymentProvider.ConfirmedPayment(
                check.paymentNo(), result.providerTradeNo(), result.amountMinor()));
    }
}

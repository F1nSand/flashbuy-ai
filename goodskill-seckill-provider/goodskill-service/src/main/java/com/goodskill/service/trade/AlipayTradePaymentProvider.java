package com.goodskill.service.trade;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

final class AlipayTradePaymentProvider implements TradePaymentProvider {

    private final AlipayClient client;
    private final String appId;
    private final String sellerId;
    private final String notifyUrl;
    private final String returnUrl;
    private final String defaultMode;
    private final String alipayCertificate;

    AlipayTradePaymentProvider(AlipayClient client, String appId, String sellerId, String notifyUrl,
                               String returnUrl, String defaultMode, String alipayCertificate) {
        this.client = client;
        this.appId = appId;
        this.sellerId = sellerId;
        this.notifyUrl = notifyUrl;
        this.returnUrl = returnUrl;
        this.defaultMode = defaultMode;
        this.alipayCertificate = alipayCertificate;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String createCheckout(CheckoutRequest checkout) {
        try {
            String mode = checkout.mode() == null ? defaultMode : checkout.mode();
            if ("wap".equals(mode)) {
                AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
                model.setOutTradeNo(checkout.paymentNo());
                model.setSubject(checkout.subject());
                model.setTotalAmount(amount(checkout.amountMinor()));
                model.setProductCode("QUICK_WAP_WAY");
                model.setTimeoutExpress(timeoutExpress(checkout.remainingSeconds()));
                AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
                request.setBizModel(model);
                request.setNotifyUrl(notifyUrl);
                request.setReturnUrl(returnUrl);
                AlipayTradeWapPayResponse response = client.pageExecute(request);
                return requireBody(response.getBody());
            }
            if (!"page".equals(mode)) {
                throw new PaymentChannelException("Unsupported Alipay checkout mode");
            }
            AlipayTradePagePayModel model = new AlipayTradePagePayModel();
            model.setOutTradeNo(checkout.paymentNo());
            model.setSubject(checkout.subject());
            model.setTotalAmount(amount(checkout.amountMinor()));
            model.setProductCode("FAST_INSTANT_TRADE_PAY");
            model.setTimeoutExpress(timeoutExpress(checkout.remainingSeconds()));
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setBizModel(model);
            request.setNotifyUrl(notifyUrl);
            request.setReturnUrl(returnUrl);
            AlipayTradePagePayResponse response = client.pageExecute(request);
            return requireBody(response.getBody());
        } catch (AlipayApiException error) {
            throw new PaymentChannelException("Could not create Alipay checkout", error);
        }
    }

    @Override
    public VerifiedNotification verifyNotification(Map<String, String> parameters) {
        try {
            if (!AlipaySignature.rsaCertCheckV1(parameters, alipayCertificate, "UTF-8", "RSA2")) {
                throw new PaymentChannelException("Alipay notification signature is invalid");
            }
        } catch (AlipayApiException error) {
            throw new PaymentChannelException("Could not verify Alipay notification signature", error);
        }
        String callbackAppId = parameters.get("app_id");
        String callbackSellerId = parameters.get("seller_id");
        String paymentNo = parameters.get("out_trade_no");
        String providerTradeNo = parameters.get("trade_no");
        String status = parameters.get("trade_status");
        if (!appId.equals(callbackAppId) || !sellerId.equals(callbackSellerId)
                || paymentNo == null || providerTradeNo == null || status == null) {
            throw new PaymentChannelException("Alipay notification does not match this merchant");
        }
        long amountMinor;
        try {
            amountMinor = new BigDecimal(parameters.get("total_amount"))
                    .setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        } catch (RuntimeException invalidAmount) {
            throw new PaymentChannelException("Alipay notification amount is invalid", invalidAmount);
        }
        return new VerifiedNotification(callbackAppId, callbackSellerId, paymentNo, providerTradeNo,
                amountMinor, status);
    }

    @Override
    public PaymentLookup query(String paymentNo) {
        try {
            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo(paymentNo);
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            request.setBizModel(model);
            AlipayTradeQueryResponse response = client.certificateExecute(request);
            if (!response.isSuccess()) {
                return new PaymentLookup(isNotFound(response.getSubCode())
                        ? PaymentStatus.NOT_FOUND : PaymentStatus.UNKNOWN, null, 0);
            }
            return paymentLookup(response);
        } catch (AlipayApiException error) {
            throw new PaymentChannelException("Could not query Alipay payment", error);
        }
    }

    @Override
    public PaymentLookup close(String paymentNo) {
        try {
            AlipayTradeCloseModel model = new AlipayTradeCloseModel();
            model.setOutTradeNo(paymentNo);
            AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
            request.setBizModel(model);
            AlipayTradeCloseResponse response = client.certificateExecute(request);
            if (response.isSuccess()) {
                return new PaymentLookup(PaymentStatus.CLOSED, response.getTradeNo(), 0);
            }
            return new PaymentLookup(isNotFound(response.getSubCode())
                    ? PaymentStatus.CLOSED : PaymentStatus.UNKNOWN, null, 0);
        } catch (AlipayApiException error) {
            throw new PaymentChannelException("Could not close Alipay payment", error);
        }
    }

    @Override
    public RefundStatus refund(RefundRequest refund) {
        try {
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setOutTradeNo(refund.paymentNo());
            model.setOutRequestNo(refund.refundNo());
            model.setRefundAmount(amount(refund.amountMinor()));
            model.setRefundReason(refund.reason());
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            request.setBizModel(model);
            AlipayTradeRefundResponse response = client.certificateExecute(request);
            return response.isSuccess() ? RefundStatus.PENDING : RefundStatus.UNKNOWN;
        } catch (AlipayApiException error) {
            throw new PaymentChannelException("Could not submit Alipay refund", error);
        }
    }

    @Override
    public RefundLookup queryRefund(String paymentNo, String refundNo) {
        try {
            AlipayTradeFastpayRefundQueryModel model = new AlipayTradeFastpayRefundQueryModel();
            model.setOutTradeNo(paymentNo);
            model.setOutRequestNo(refundNo);
            AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
            request.setBizModel(model);
            AlipayTradeFastpayRefundQueryResponse response = client.certificateExecute(request);
            if (!response.isSuccess()) {
                return new RefundLookup(isNotFound(response.getSubCode())
                        ? RefundStatus.PENDING : RefundStatus.UNKNOWN, 0);
            }
            if (!paymentNo.equals(response.getOutTradeNo()) || !refundNo.equals(response.getOutRequestNo())) {
                return new RefundLookup(RefundStatus.UNKNOWN, 0);
            }
            RefundStatus status = switch (response.getRefundStatus() == null ? "" : response.getRefundStatus()) {
                case "REFUND_SUCCESS" -> RefundStatus.SUCCEEDED;
                case "REFUND_PROCESSING" -> RefundStatus.PENDING;
                case "REFUND_FAIL" -> RefundStatus.FAILED;
                default -> RefundStatus.UNKNOWN;
            };
            long amount = 0;
            if (status == RefundStatus.SUCCEEDED || status == RefundStatus.PENDING) {
                try {
                    amount = new BigDecimal(response.getRefundAmount()).setScale(2, RoundingMode.UNNECESSARY)
                            .movePointRight(2).longValueExact();
                } catch (RuntimeException invalidAmount) {
                    return new RefundLookup(RefundStatus.UNKNOWN, 0);
                }
            }
            return new RefundLookup(status, amount);
        } catch (AlipayApiException error) {
            throw new PaymentChannelException("Could not query Alipay refund", error);
        }
    }

    private static PaymentStatus paymentStatus(String status) {
        return switch (status == null ? "" : status) {
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> PaymentStatus.PAID;
            case "WAIT_BUYER_PAY" -> PaymentStatus.WAITING;
            case "TRADE_CLOSED" -> PaymentStatus.CLOSED;
            default -> PaymentStatus.UNKNOWN;
        };
    }

    private static PaymentLookup paymentLookup(AlipayTradeQueryResponse response) {
        PaymentStatus status = paymentStatus(response.getTradeStatus());
        long amount = 0;
        if (status == PaymentStatus.PAID) {
            try {
                amount = new BigDecimal(response.getTotalAmount()).setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2).longValueExact();
            } catch (RuntimeException invalidAmount) {
                return new PaymentLookup(PaymentStatus.UNKNOWN, null, 0);
            }
        }
        return new PaymentLookup(status, response.getTradeNo(), amount);
    }

    private static boolean isNotFound(String subCode) {
        return "ACQ.TRADE_NOT_EXIST".equals(subCode);
    }

    private static String amount(long amountMinor) {
        if (amountMinor <= 0) {
            throw new PaymentChannelException("Alipay amount must be positive");
        }
        return BigDecimal.valueOf(amountMinor, 2).toPlainString();
    }

    private static String timeoutExpress(long remainingSeconds) {
        if (remainingSeconds < 60) {
            throw new PaymentChannelException("Alipay checkout has less than one minute remaining");
        }
        long minutes = Math.floorDiv(Math.addExact(remainingSeconds, 59), 60);
        return minutes + "m";
    }

    private static String requireBody(String body) {
        if (body == null || body.isBlank()) {
            throw new PaymentChannelException("Alipay returned an empty checkout form");
        }
        return body;
    }
}

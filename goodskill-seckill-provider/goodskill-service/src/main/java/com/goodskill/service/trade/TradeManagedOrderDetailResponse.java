package com.goodskill.service.trade;

public record TradeManagedOrderDetailResponse(
        TradeManagedOrderResponse order, CreateTradeOrderRequest.ShippingAddress shippingAddress,
        String carrierCode, String trackingNo, String refundState) {
}

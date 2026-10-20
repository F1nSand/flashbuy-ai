package com.goodskill.service.trade;

public record TradeOrderDetailResponse(
        TradeOrderResponse order,
        CreateTradeOrderRequest.ShippingAddress shippingAddress,
        String fulfillmentState,
        String productName,
        String skuName,
        int quantity) {
}

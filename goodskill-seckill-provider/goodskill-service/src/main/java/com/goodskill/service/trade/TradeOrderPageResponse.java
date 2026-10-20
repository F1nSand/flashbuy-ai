package com.goodskill.service.trade;

import java.util.List;

public record TradeOrderPageResponse(long page, int size, long total, List<TradeOrderResponse> orders) {
}

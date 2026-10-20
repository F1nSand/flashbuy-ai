package com.goodskill.service.trade;

import java.util.List;

public record TradeManagedOrderPageResponse(long page, int size, long total, List<TradeManagedOrderResponse> orders) {
}

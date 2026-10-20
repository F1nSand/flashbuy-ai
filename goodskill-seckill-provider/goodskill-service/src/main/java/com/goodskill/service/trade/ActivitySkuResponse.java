package com.goodskill.service.trade;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivitySkuResponse(
        long skuId,
        String skuCode,
        String productName,
        String skuName,
        long unitPriceMinor,
        int purchaseLimitPerUser,
        boolean inStock,
        Long availableQuantity,
        Long allocatedQuantity) {
}
